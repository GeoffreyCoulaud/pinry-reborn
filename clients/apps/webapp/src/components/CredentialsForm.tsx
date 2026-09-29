import { Button, Checkbox, Input, Label, TextField } from "@heroui/react";
import { useNavigate } from "@tanstack/react-router";
import type { ReactNode } from "react";
import { m } from "../paraglide/messages.js";
import type { OpenSessionMutation } from "../session";
import { AppHeader } from "./AppHeader";

interface CredentialsFormProps {
	/** Names the screen and its button, which ask for the same thing. */
	title: string;
	refusal: string;
	newPassword: boolean;
	session: OpenSessionMutation;
	footer: ReactNode;
}

export function CredentialsForm({
	title,
	refusal,
	newPassword,
	session,
	footer,
}: CredentialsFormProps) {
	const navigate = useNavigate();
	return (
		<main className="mx-auto flex max-w-sm flex-col gap-4 p-8">
			<AppHeader heading={title} />
			<form
				className="flex flex-col gap-3"
				onSubmit={(event) => {
					event.preventDefault();
					const fields = new FormData(event.currentTarget);
					const name = String(fields.get("name"));
					const password = String(fields.get("password"));
					session.mutate(
						{
							credentials: { name, password },
							rememberMe: fields.get("rememberMe") !== null,
						},
						{ onSuccess: () => void navigate({ to: "/" }) },
					);
				}}
			>
				<TextField name="name" isRequired autoComplete="username">
					<Label>{m.username()}</Label>
					<Input />
				</TextField>
				<TextField
					name="password"
					type="password"
					isRequired
					autoComplete={newPassword ? "new-password" : "current-password"}
				>
					<Label>{m.password()}</Label>
					<Input />
				</TextField>
				<Checkbox name="rememberMe">
					<Checkbox.Content>
						<Checkbox.Control>
							<Checkbox.Indicator />
						</Checkbox.Control>
						{m.remember_me()}
					</Checkbox.Content>
				</Checkbox>
				{session.isError ? <p role="alert">{refusal}</p> : null}
				<Button type="submit" isDisabled={session.isPending}>
					{title}
				</Button>
			</form>
			{footer}
		</main>
	);
}
