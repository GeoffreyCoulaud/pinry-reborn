import {
	useInfiniteQuery,
	useMutation,
	useQuery,
	useQueryClient,
} from "@tanstack/react-query";
import { auth, bodyOf } from "./api";
import {
	dropUpload,
	type Import,
	pruneRecords,
	send,
	writeRecord,
} from "./imports";
import { POLL_MS } from "./lib/downloads";
import { AccountRefusal } from "./me";

// The latest import, which the upload changes at each end it reaches.
const LATEST = ["imports", "latest"];

/** The newest import or none, as the export's section reads its own. */
export function useLatestImport() {
	return useQuery({
		queryKey: LATEST,
		queryFn: async () => {
			const query = { pageSize: 1 };
			const answer = await auth.client.GET("/api/v1/me/imports", {
				params: { query },
			});
			const latest = bodyOf(answer, "the imports").imports[0] ?? null;
			pruneRecords(latest);
			return latest;
		},
		refetchInterval: (query) => {
			const state = query.state.data?.state;
			return state === "PENDING" || state === "RUNNING" ? POLL_MS : false;
		},
	});
}

/** One import's report, a page at a time on the cursor each page answers. */
export function useImportIssues(id: string) {
	return useInfiniteQuery({
		queryKey: ["imports", id, "issues"],
		queryFn: async ({ pageParam }) => {
			const params = { path: { id }, query: { cursor: pageParam } };
			const answer = await auth.client.GET("/api/v1/me/imports/{id}/issues", {
				params,
			});
			return bodyOf(answer, "the report");
		},
		initialPageParam: undefined as string | undefined,
		getNextPageParam: (page) => page.pagination.nextCursor ?? undefined,
	});
}

/** A new import, and the upload of its archive one chunk at a time. */
export function useStartImport() {
	const queryClient = useQueryClient();
	const settle = () => queryClient.invalidateQueries({ queryKey: LATEST });
	return useMutation({
		mutationFn: async ({
			file,
			chunkBytes,
		}: {
			file: File;
			chunkBytes: number;
		}) => {
			const { data, error, response } =
				await auth.client.POST("/api/v1/me/imports");
			if (data === undefined) {
				throw new AccountRefusal(error, response.status);
			}
			writeRecord(data.id, file);
			dropUpload();
			void send(
				{ importId: data.id, file, chunkBytes, settle },
				{ next: "SEND", offset: 0, failures: 0 },
			);
		},
		onSuccess: settle,
	});
}

/** The same file chosen after a reload: the upload goes on at the row's length. */
export function useResumeImport() {
	const queryClient = useQueryClient();
	const settle = () => queryClient.invalidateQueries({ queryKey: LATEST });
	return (row: Import, file: File, chunkBytes: number) => {
		dropUpload();
		const from = {
			next: "SEND",
			offset: row.uploadedBytes,
			failures: 0,
		} as const;
		void send({ importId: row.id, file, chunkBytes, settle }, from);
	};
}

/** What the import already created stays: `DELETE` cancels what is left. */
export function useCancelImport() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: async (id: string) => {
			// `response.ok` and never `data`: a 204 leaves it undefined.
			const { response } = await auth.client.DELETE("/api/v1/me/imports/{id}", {
				params: { path: { id } },
			});
			if (!response.ok) {
				throw new Error(`The API kept the import: ${response.status}.`);
			}
		},
		// The upload runs on until the row that replaces it is read, so a refused cancel leaves it be.
		onSettled: async (_, error) => {
			await queryClient.invalidateQueries({ queryKey: LATEST });
			if (error === null) {
				dropUpload();
			}
		},
	});
}
