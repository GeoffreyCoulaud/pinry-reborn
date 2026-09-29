import type { ReactNode } from "react";

export function TaskItem({
	title,
	children,
}: {
	title: string;
	children: ReactNode;
}) {
	return (
		<li className="flex flex-col items-start gap-1 border-b border-separator py-2 last:border-0">
			<span className="font-medium">{title}</span>
			{children}
		</li>
	);
}
