import { ImageOff } from "lucide-react";
import { type CSSProperties, useState } from "react";
import { m } from "../paraglide/messages.js";

/**
 * A rendition, or what stands in its place where the server cannot draw it (specification
 * 2026-10-04, decision D). The failure is the source's, so a tile's still survives its animation.
 */
export function RenditionImage({
	src,
	alt,
	className = "",
	style,
	loading,
}: {
	src: string;
	alt: string;
	className?: string;
	style?: CSSProperties;
	loading?: "lazy";
}) {
	const [failed, setFailed] = useState<string | null>(null);

	if (failed !== src) {
		return (
			// biome-ignore lint/a11y/noNoninteractiveElementInteractions: a load failure is no user interaction.
			<img
				src={src}
				alt={alt}
				className={className}
				style={style}
				loading={loading}
				onError={() => setFailed(src)}
			/>
		);
	}
	// A decorative image's stand-in is decorative too, or it joins the name of the link around it.
	const named =
		alt === ""
			? { "aria-hidden": true }
			: { role: "img", "aria-label": m.preview_unavailable() };
	return (
		<span
			{...named}
			title={m.preview_unavailable()}
			style={style}
			className={`${className} flex flex-col items-center justify-center gap-2 rounded bg-surface p-2 shadow-surface text-center text-muted text-sm`}
		>
			<ImageOff aria-hidden className="size-6 shrink-0" />
			{/* A decorative thumbnail is too small for the sentence, and its row already names the pin. */}
			{alt === "" ? null : <span aria-hidden>{m.preview_unavailable()}</span>}
		</span>
	);
}
