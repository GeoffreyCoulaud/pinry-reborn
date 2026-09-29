import { Link } from "@tanstack/react-router";
import { CredentialsForm } from "../components/CredentialsForm";
import { m } from "../paraglide/messages.js";
import { useSignUp } from "../session";

export function SignUp() {
	const signUp = useSignUp();
	return (
		<CredentialsForm
			title={m.sign_up()}
			refusal={m.sign_up_refused()}
			newPassword={true}
			session={signUp}
			footer={<Link to="/sign-in">{m.sign_in()}</Link>}
		/>
	);
}
