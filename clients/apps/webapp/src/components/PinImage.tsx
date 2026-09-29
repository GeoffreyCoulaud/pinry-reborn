import { Button, Spinner } from "@heroui/react";
import { useEffect, useRef, useState } from "react";
import { downloadReason, retriable } from "../downloadReasons";
import { useSetPinImage } from "../images";
import { type Rendition, tileImageSource } from "../lib/tiles";
import { m } from "../paraglide/messages.js";
import type { Pin } from "../pins";

/** How long the original may take before a spinner says it is coming, as long as a drop's reading. */
const SPINNER_DELAY_MS = 300;

/**
 * The original in a box of the size it is drawn at, its own or less to fit, never more. The grid's
 * rendition fills that box until the original arrives, from the cache when its tile was drawn.
 */
function OriginalImage({
	url,
	width,
	height,
	alt,
	placeholder,
}: {
	url: string;
	width: number;
	height: number;
	alt: string;
	placeholder: Rendition;
}) {
	const original = useRef<HTMLImageElement>(null);
	const [state, setState] = useState<"loading" | "decoded" | "failed">(
		"loading",
	);
	const [slow, setSlow] = useState(false);
	const size = {
		aspectRatio: `${width} / ${height}`,
		width: `min(${width}px, 100%, calc(var(--fit-height) * ${width / height}))`,
	};

	useEffect(() => {
		let shown = true;
		const late = setTimeout(() => setSlow(true), SPINNER_DELAY_MS);
		// Decoded, not loaded: swapped on `load`, the placeholder went a frame before the original could paint.
		original.current?.decode().then(
			() => shown && setState("decoded"),
			() => shown && setState("failed"),
		);
		return () => {
			shown = false;
			clearTimeout(late);
		};
	}, []);

	return (
		<div className="relative" style={size}>
			{state !== "decoded" && (
				<img
					src={tileImageSource(url, placeholder)}
					alt=""
					className="absolute inset-0 h-full w-full"
				/>
			)}
			{/* Transparent rather than hidden until decoded: Firefox draws the alt text over the placeholder. */}
			<img
				ref={original}
				src={url}
				alt={alt}
				className={`absolute inset-0 h-full w-full ${state === "decoded" ? "" : "opacity-0"}`}
			/>
			{state === "loading" && slow && (
				<span className="absolute end-2 bottom-2 flex rounded-full bg-overlay p-1 shadow-surface">
					<Spinner size="sm" aria-label={m.image_original_loading()} />
				</span>
			)}
		</div>
	);
}

/**
 * The image side: the picture, or what stands in its place (specification 2026-09-27, decision C).
 * The form leaves Retry out, its own choice being where the image is fetched from.
 */
export function PinImage({
	pin,
	placeholder,
	retries = true,
}: {
	pin: Pin;
	placeholder: Rendition;
	retries?: boolean;
}) {
	const retry = useSetPinImage();
	const image = pin.image;
	const address = pin.sourceMediaUrl;

	if (image?.status === "READY" && image.url != null) {
		return image.width != null && image.height != null ? (
			<OriginalImage
				key={image.url}
				url={image.url}
				width={image.width}
				height={image.height}
				alt={pin.description}
				placeholder={placeholder}
			/>
		) : (
			<img
				src={image.url}
				alt={pin.description}
				className="max-h-full max-w-full max-lg:max-h-[70dvh]"
			/>
		);
	}
	if (image?.status === "PENDING") {
		return (
			<p role="status" className="flex items-center gap-2">
				<Spinner aria-hidden />
				{m.task_running()}
			</p>
		);
	}
	if (image?.status === "FAILED") {
		return (
			<div className="flex flex-col items-center gap-3 text-center">
				<p>{downloadReason(image.reasonCode, image.message)}</p>
				{/* From the pin's own address, which the user may have corrected since the download failed. */}
				{retries && retriable(image.reasonCode) && address != null && (
					<Button
						isDisabled={retry.isPending}
						onPress={() =>
							retry.mutate({ pinId: pin.id, source: { url: address } })
						}
					>
						{m.retry()}
					</Button>
				)}
				{retry.isError ? <p role="alert">{m.image_refused()}</p> : null}
			</div>
		);
	}
	return <p>{m.pin_no_image()}</p>;
}
