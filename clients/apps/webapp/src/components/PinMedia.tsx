import { Button, Spinner } from "@heroui/react";
import { useEffect, useRef, useState } from "react";
import { downloadReason, retriable } from "../downloadReasons";
import { isPlayable, isVideo, videoFileName } from "../lib/media";
import { type Rendition, tileMediaSource } from "../lib/tiles";
import { useSetPinMedia } from "../media";
import { m } from "../paraglide/messages.js";
import type { Pin } from "../pins";
import { RenditionImage } from "./RenditionImage";

/** How long the original may take before a spinner says it is coming, as long as a drop's reading. */
const SPINNER_DELAY_MS = 300;

/** The box the media is drawn in: its own size or less to fit, never more. */
function fitted(width: number, height: number) {
	return {
		aspectRatio: `${width} / ${height}`,
		width: `min(${width}px, 100%, calc(var(--fit-height) * ${width / height}))`,
	};
}

/** A square to fit, for a video stored without its dimensions. */
const SQUARE = { aspectRatio: "1 / 1", width: "min(100%, var(--fit-height))" };

/**
 * The original in a box of the size it is drawn at, its own or less to fit, never more. The grid's
 * rendition fills that box until the original arrives, from the cache when its tile was drawn.
 */
function OriginalMedia({
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
		<div className="relative" style={fitted(width, height)}>
			{state === "decoded" ? null : (
				<img
					src={tileMediaSource(url, placeholder)}
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
			{state === "loading" && slow ? (
				<span className="absolute end-2 bottom-2 flex rounded-full bg-overlay p-1 shadow-surface">
					<Spinner size="sm" aria-label={m.media_original_loading()} />
				</span>
			) : null}
		</div>
	);
}

/**
 * The original played under the grid's rendition, or that rendition and a link to the bytes where
 * this browser cannot decode them (ADR 0047, decision 6).
 */
function VideoMedia({
	url,
	mimeType,
	width,
	height,
	alt,
	placeholder,
}: {
	url: string;
	mimeType: string;
	width: number | null | undefined;
	height: number | null | undefined;
	alt: string;
	placeholder: Rendition;
}) {
	const [failed, setFailed] = useState(
		() => !isPlayable(document.createElement("video").canPlayType(mimeType)),
	);
	const poster = tileMediaSource(url, placeholder);
	const box = width != null && height != null ? fitted(width, height) : SQUARE;

	if (failed) {
		return (
			<div className="flex w-full flex-col items-center gap-3 text-center">
				<RenditionImage src={poster} alt={alt} style={box} />
				<p>{m.video_unplayable()}</p>
				<a
					href={url}
					download={videoFileName(mimeType)}
					className="text-accent hover:underline"
				>
					{m.video_download()}
				</a>
			</div>
		);
	}
	return (
		// biome-ignore lint/a11y/useMediaCaption: a pin's video carries no captions to offer.
		<video
			src={url}
			poster={poster}
			controls
			aria-label={alt}
			style={box}
			onError={() => setFailed(true)}
		/>
	);
}

/**
 * The image side: the picture, or what stands in its place (specification 2026-09-27, decision C).
 * The form leaves Retry out, its own choice being where the image is fetched from.
 */
export function PinMedia({
	pin,
	placeholder,
	retries = true,
}: {
	pin: Pin;
	placeholder: Rendition;
	retries?: boolean;
}) {
	const retry = useSetPinMedia();
	const media = pin.media;
	const address = pin.sourceMediaUrl;

	if (media?.status === "READY" && media.url != null) {
		if (isVideo(media.mimeType)) {
			return (
				<VideoMedia
					// The address survives a replacement, so the stored size and type tell the new video apart.
					key={`${media.url}:${media.byteSize}:${media.mimeType}`}
					url={media.url}
					mimeType={media.mimeType}
					width={media.width}
					height={media.height}
					alt={pin.description}
					placeholder={placeholder}
				/>
			);
		}
		return media.width != null && media.height != null ? (
			<OriginalMedia
				key={media.url}
				url={media.url}
				width={media.width}
				height={media.height}
				alt={pin.description}
				placeholder={placeholder}
			/>
		) : (
			<img
				src={media.url}
				alt={pin.description}
				className="max-h-full max-w-full max-lg:max-h-[70dvh]"
			/>
		);
	}
	if (media?.status === "PENDING") {
		return (
			<p role="status" className="flex items-center gap-2">
				<Spinner aria-hidden />
				{m.task_running()}
			</p>
		);
	}
	if (media?.status === "FAILED") {
		return (
			<div className="flex flex-col items-center gap-3 text-center">
				<p>{downloadReason(media.reasonCode, media.message)}</p>
				{/* From the pin's own address, which the user may have corrected since the download failed. */}
				{retries && retriable(media.reasonCode) && address != null ? (
					<Button
						isDisabled={retry.isPending}
						onPress={() =>
							retry.mutate({ pinId: pin.id, source: { url: address } })
						}
					>
						{m.retry()}
					</Button>
				) : null}
				{retry.isError ? <p role="alert">{m.media_refused()}</p> : null}
			</div>
		);
	}
	return <p>{m.pin_no_media()}</p>;
}
