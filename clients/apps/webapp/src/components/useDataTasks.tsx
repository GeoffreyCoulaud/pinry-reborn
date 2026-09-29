import { Button, buttonVariants } from "@heroui/react";
import { type ReactNode, useEffect, useState } from "react";
import { exportFailure, importFailure } from "../dataFailures";
import {
	downloadHref,
	type Export,
	exportReadiness,
	useLatestExport,
} from "../exports";
import { useLatestImport } from "../importQueries";
import { type Import, importProgress, useUpload } from "../imports";
import { dataNotices } from "../lib/notices";
import { m } from "../paraglide/messages.js";
import { ImportCounters, UploadProgress } from "./ImportSection";
import { TaskItem } from "./TaskItem";

// Per browser: another one shows a dismissed notice once more, which is harmless.
const DISMISSED = "pinry-dismissed-notices";

function readDismissed(): string[] {
	try {
		return JSON.parse(localStorage.getItem(DISMISSED) ?? "[]") as string[];
	} catch {
		return [];
	}
}

/**
 * The upload, the import and the export while they run, then what the latest of each ended with
 * until it is dismissed (specification 2026-09-25, decisions F4 and G3). One item per task.
 */
export function useDataTasks(): ReactNode[] {
	const upload = useUpload();
	const latestExport = useLatestExport();
	const latestImport = useLatestImport();
	const [dismissed, setDismissed] = useState(readDismissed);
	const exported = latestExport.data ?? null;
	const imported = latestImport.data ?? null;
	const notices = dataNotices(exported, imported, dismissed, Date.now());
	const read = latestExport.isSuccess && latestImport.isSuccess;
	const kept = JSON.stringify(notices.dismissed);

	// Only once both rows are read: an unread row would drop the dismissal of its notice.
	useEffect(() => {
		try {
			if (read && kept === "[]") {
				localStorage.removeItem(DISMISSED);
			} else if (read) {
				localStorage.setItem(DISMISSED, kept);
			}
		} catch {
			// A private window forgets the dismissals with the tab.
		}
	}, [read, kept]);

	const dismiss = (id: string) => (
		<Button
			size="sm"
			variant="secondary"
			onPress={() => setDismissed((ids) => [...ids, id])}
		>
			{m.dismiss()}
		</Button>
	);
	const tasks: ReactNode[] = [];

	if (upload !== null) {
		tasks.push(
			<TaskItem key="upload" title={m.task_import()}>
				<UploadProgress upload={upload} />
			</TaskItem>,
		);
	} else if (imported?.state === "PENDING" || imported?.state === "RUNNING") {
		tasks.push(
			<TaskItem key="import" title={m.task_import()}>
				<p className="text-sm">
					{imported.state === "PENDING"
						? m.import_pending()
						: importProgress(imported)}
				</p>
			</TaskItem>,
		);
	}
	if (exported?.state === "PENDING") {
		tasks.push(
			<TaskItem key="export" title={m.task_export()}>
				<p className="text-sm">{m.export_pending()}</p>
			</TaskItem>,
		);
	}
	if (notices.export !== null) {
		tasks.push(exportNotice(notices.export, dismiss(notices.export.id)));
	}
	if (notices.import !== null) {
		tasks.push(importNotice(notices.import, dismiss(notices.import.id)));
	}
	return tasks;
}

function exportNotice(row: Export, dismissal: ReactNode): ReactNode {
	const ready = row.state === "READY";
	return (
		<TaskItem key={row.id} title={m.task_export()}>
			<p className="text-sm">
				{ready ? exportReadiness(row) : exportFailure(row.reasonCode)}
			</p>
			<div className="flex flex-wrap items-center gap-2">
				{ready ? (
					<a
						className={buttonVariants({ size: "sm" })}
						href={downloadHref(row)}
					>
						{m.export_download()}
					</a>
				) : null}
				{dismissal}
			</div>
		</TaskItem>
	);
}

function importNotice(row: Import, dismissal: ReactNode): ReactNode {
	return (
		<TaskItem key={row.id} title={m.task_import()}>
			{row.state === "COMPLETED" ? (
				<>
					<p className="text-sm">{m.import_completed()}</p>
					<div className="text-sm">
						<ImportCounters row={row} />
					</div>
				</>
			) : (
				<p className="text-sm">
					{row.state === "ABANDONED"
						? m.import_abandoned()
						: importFailure(row.reasonCode)}
				</p>
			)}
			{dismissal}
		</TaskItem>
	);
}
