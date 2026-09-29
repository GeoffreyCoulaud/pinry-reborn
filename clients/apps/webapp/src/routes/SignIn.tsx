import { Link } from "@tanstack/react-router";
import { CredentialsForm } from "../components/CredentialsForm";
import { m } from "../paraglide/messages.js";
import { useSignIn } from "../session";

export function SignIn() {
	const signIn = useSignIn();
	return (
		<CredentialsForm
			title={m.sign_in()}
			refusal={m.sign_in_refused()}
			newPassword={false}
			session={signIn}
			footer={<Link to="/sign-up">{m.sign_up()}</Link>}
		/>
	);
}
