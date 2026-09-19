import { Button, Checkbox, Input, Label, TextField } from "@heroui/react"
import { Link, useNavigate } from "@tanstack/react-router"
import type { ReactNode } from "react"
import { m } from "../paraglide/messages.js"
import { useSignIn, useSignUp, type OpenSessionMutation } from "../session"

interface CredentialsFormProps {
  /** Names the screen and its button, which ask for the same thing. */
  title: string
  refusal: string
  newPassword: boolean
  session: OpenSessionMutation
  footer: ReactNode
}

function CredentialsForm({ title, refusal, newPassword, session, footer }: CredentialsFormProps) {
  const navigate = useNavigate()
  return (
    <main className="mx-auto flex max-w-sm flex-col gap-4 p-8">
      <h1 className="text-2xl font-semibold">{title}</h1>
      <form
        className="flex flex-col gap-3"
        onSubmit={(event) => {
          event.preventDefault()
          const fields = new FormData(event.currentTarget)
          const name = String(fields.get("name"))
          const password = String(fields.get("password"))
          session.mutate(
            { credentials: { name, password }, rememberMe: fields.get("rememberMe") !== null },
            { onSuccess: () => void navigate({ to: "/" }) },
          )
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
        {session.isError && <p role="alert">{refusal}</p>}
        <Button type="submit" isDisabled={session.isPending}>
          {title}
        </Button>
      </form>
      {footer}
    </main>
  )
}

export function SignIn() {
  const signIn = useSignIn()
  return (
    <CredentialsForm
      title={m.sign_in()}
      refusal={m.sign_in_refused()}
      newPassword={false}
      session={signIn}
      footer={<Link to="/sign-up">{m.sign_up()}</Link>}
    />
  )
}

export function SignUp() {
  const signUp = useSignUp()
  return (
    <CredentialsForm
      title={m.sign_up()}
      refusal={m.sign_up_refused()}
      newPassword={true}
      session={signUp}
      footer={<Link to="/sign-in">{m.sign_in()}</Link>}
    />
  )
}
