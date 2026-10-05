package fr.geoffreyCoulaud.pinryReborn.api.domain.media

/** A frame's PDQ hash, its 256 bits in four words from the most significant, and its quality from 0 to 100. */
data class PdqHash(val words: List<Long>, val quality: Int) {
    fun distanceTo(other: PdqHash) = distance(words, other.words)

    companion object {
        /** The Hamming distance between two hashes' words. */
        fun distance(first: List<Long>, second: List<Long>) =
            first.zip(second).sumOf { (mine, theirs) -> (mine xor theirs).countOneBits() }
    }
}

/**
 * Meta's PDQ (`facebook/ThreatExchange`, `hashing/hashing.pdf`), in single precision and in the order `pdq/cpp` sums,
 * so that the same luminance gives exactly the reference's hash.
 */
object PdqHasher {
    /** Meta's recommended thresholds (`pdq/README.md`): a match within 31 bits, a quality of 49 or less discarded. */
    const val MATCH_DISTANCE = 31
    const val DISCARDED_QUALITY = 49

    private const val MIN_SIDE = 5
    private const val SIDE = 64
    private const val DCT_SIDE = 16
    private const val BLUR_PASSES = 2
    private const val WORDS = DCT_SIDE * DCT_SIDE / Long.SIZE_BITS
    private const val PIXEL_CENTRE = 0.5
    private const val PERCENT = 100
    private const val LUMA_RANGE = 255
    private const val GRADIENTS_PER_POINT = 90
    private const val MAX_QUALITY = 100

    private val DCT = FloatArray(DCT_SIDE * SIDE) { index ->
        val (row, column) = index / SIDE to index % SIDE
        val scale = Math.sqrt(2.0 / SIDE).toFloat()
        (scale * Math.cos(Math.PI / 2.0 / SIDE * (row + 1) * (2 * column + 1))).toFloat()
    }

    fun hash(frame: LumaFrame): PdqHash {
        if (minOf(frame.width, frame.height) < MIN_SIDE) return PdqHash(List(WORDS) { 0L }, quality = 0)
        val image = downsampled(frame)
        return PdqHash(bits(dct(image)), quality(image))
    }

    // A frame already 64 by 64 is taken as it is; any other is blurred by a tent filter, then decimated.
    private fun downsampled(frame: LumaFrame): FloatArray {
        val (width, height) = frame.width to frame.height
        if (width == SIDE && height == SIDE) return frame.luma
        val blurred = frame.luma.copyOf()
        val scratch = FloatArray(blurred.size)
        repeat(BLUR_PASSES) {
            for (row in 0 until height) box(blurred, scratch, row * width until (row + 1) * width, window(width))
            for (column in 0 until width) box(scratch, blurred, column until width * height step width, window(height))
        }
        return FloatArray(SIDE * SIDE) { index ->
            val row = ((index / SIDE + PIXEL_CENTRE) * height / SIDE).toInt()
            val column = ((index % SIDE + PIXEL_CENTRE) * width / SIDE).toInt()
            blurred[row * width + column]
        }
    }

    // Half of one 64th of the side, rounded up: two passes of it cover a whole 64th.
    private fun window(side: Int) = (side + 2 * SIDE - 1) / (2 * SIDE)

    // A box mean along [line], its window shrunk at both ends, summed in the order `box1DFloat` sums.
    private fun box(input: FloatArray, output: FloatArray, line: IntProgression, window: Int) {
        val at = line.toList()
        val half = (window + 2) / 2
        var sum = 0f
        var count = 0
        var (read, left, written) = Triple(0, 0, 0)
        fun add() {
            sum += input[at[read++]]
            count++
        }
        fun drop() {
            sum -= input[at[left++]]
            count--
        }
        fun write() {
            output[at[written++]] = sum / count
        }
        repeat(half - 1) { add() }
        repeat(window - half + 1) {
            add()
            write()
        }
        repeat(at.size - window) {
            add()
            drop()
            write()
        }
        repeat(half - 1) {
            drop()
            write()
        }
    }

    // The sum of the significant gradients between neighbours, each truncated to a percent of the range.
    private fun quality(image: FloatArray): Int {
        fun step(from: Int, to: Int) = Math.abs(((image[from] - image[to]) * PERCENT / LUMA_RANGE).toInt())
        var sum = 0
        for (row in 0 until SIDE - 1) for (column in 0 until SIDE) {
            sum += step(row * SIDE + column, (row + 1) * SIDE + column)
        }
        for (row in 0 until SIDE) for (column in 0 until SIDE - 1) {
            sum += step(row * SIDE + column, row * SIDE + column + 1)
        }
        return minOf(sum / GRADIENTS_PER_POINT, MAX_QUALITY)
    }

    // The 16 by 16 lowest frequencies but the constant one, as `D A Dᵗ`.
    private fun dct(image: FloatArray): FloatArray {
        val partial = FloatArray(DCT_SIDE * SIDE) { index ->
            val (row, column) = index / SIDE to index % SIDE
            var sum = 0f
            for (k in 0 until SIDE) sum += DCT[row * SIDE + k] * image[k * SIDE + column]
            sum
        }
        return FloatArray(DCT_SIDE * DCT_SIDE) { index ->
            val (row, column) = index / DCT_SIDE to index % DCT_SIDE
            var sum = 0f
            for (k in 0 until SIDE) sum += partial[row * SIDE + k] * DCT[column * SIDE + k]
            sum
        }
    }

    // Bit `i` is set when the `i`th coefficient is above their median.
    private fun bits(coefficients: FloatArray): List<Long> {
        val median = torben(coefficients)
        val words = LongArray(WORDS)
        for (bit in coefficients.indices) {
            val word = WORDS - 1 - bit / Long.SIZE_BITS
            if (coefficients[bit] > median) words[word] = words[word] or (1L shl bit % Long.SIZE_BITS)
        }
        return words.toList()
    }

    // Torben Mogensen's median as `torben.cpp` finds it; its third outcome needs an odd count, and 256 is even.
    private fun torben(values: FloatArray): Float {
        var min = values.min()
        var max = values.max()
        val half = (values.size + 1) / 2
        while (true) {
            val guess = (min + max) / 2
            val below = values.filter { it < guess }
            val above = values.filter { it > guess }
            if (below.size <= half && above.size <= half) return if (below.size >= half) below.max() else guess
            if (below.size > above.size) max = below.max() else min = above.min()
        }
    }
}
