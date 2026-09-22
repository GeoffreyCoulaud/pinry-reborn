import { AlertDialog, Button, Input, Label, TextField, toast } from "@heroui/react"
import { AppHeader } from "../components/AppHeader"
import { AppNav } from "../components/AppNav"
import { useChangePassword, useDeleteAccount, useMe } from "../me"
import { m } from "../paraglide/messages.js"
import { passwordRefusal } from "../passwordRefusals"
import { useSignOutEverywhere } from "../session"

function PasswordField({
  name,
  label,
  autoComplete,
}: {
  name: string
  label: string
  autoComplete: "current-password" | "new-password"
}) {
  return (
    <TextField name={name} type="password" isRequired autoComplete={autoComplete}>
      <Label>{label}</Label>
      <Input />
    </TextField>
  )
}

/** Why a write was refused, where the gesture that earned it happened. */
function Refusal({ code }: { code: string | null }) {
  return <p role="alert">{passwordRefusal(code)}</p>
}

/** The password, and with it the sessions the old one opened (decisions C and H). */
function PasswordForm() {
  const change = useChangePassword()

  return (
    <section className="flex flex-col gap-2">
      <h3 className="text-lg font-semibold">{m.change_password()}</h3>
      {/* The consequence before the gesture, not after it (decision C). */}
      <p className="text-muted">{m.session_ends_note()}</p>
      <form
        className="flex max-w-sm flex-col gap-3"
        onSubmit={(event) => {
          event.preventDefault()
          const fields = new FormData(event.currentTarget)
          change.mutate({
            currentPassword: String(fields.get("currentPassword")),
            newPassword: String(fields.get("newPassword")),
          })
        }}
      >
        <PasswordField
          name="currentPassword"
          label={m.current_password()}
          autoComplete="current-password"
        />
        <PasswordField name="newPassword" label={m.new_password()} autoComplete="new-password" />
        {change.error !== null && <Refusal code={change.error.code} />}
        <Button type="submit" className="self-start" isDisabled={change.isPending}>
          {m.change_password()}
        </Button>
      </form>
    </section>
  )
}

/**
 * The password again, in a dialog: it is the factor `X-Reauthentication` requires anyway, so the
 * friction is real rather than decorative, and a delete button never sits armed on an open screen
 * (decision B). The button is the trigger itself, `AlertDialog` being a `DialogTrigger`, which
 * presses its first child. Nothing closes the dialog on a refusal: the retry is inside it.
 */
function DeleteAccount() {
  const remove = useDeleteAccount()

  return (
    <AlertDialog>
      <Button variant="danger">{m.delete_account()}</Button>
      <AlertDialog.Backdrop>
        <AlertDialog.Container size="sm">
          <AlertDialog.Dialog>
            {({ close }) => (
              <form
                className="flex flex-col gap-3"
                onSubmit={(event) => {
                  event.preventDefault()
                  const fields = new FormData(event.currentTarget)
                  remove.mutate(String(fields.get("password")))
                }}
              >
                <AlertDialog.Heading>{m.delete_account_question()}</AlertDialog.Heading>
                <AlertDialog.Body className="flex flex-col gap-3">
                  {m.delete_account_warning()}
                  <PasswordField
                    name="password"
                    label={m.password()}
                    autoComplete="current-password"
                  />
                  {remove.error !== null && <Refusal code={remove.error.code} />}
                </AlertDialog.Body>
                <AlertDialog.Footer>
                  <Button variant="ghost" onPress={close}>
                    {m.cancel()}
                  </Button>
                  <Button type="submit" variant="danger" isDisabled={remove.isPending}>
                    {m.delete_account_confirm()}
                  </Button>
                </AlertDialog.Footer>
              </form>
            )}
          </AlertDialog.Dialog>
        </AlertDialog.Container>
      </AlertDialog.Backdrop>
    </AlertDialog>
  )
}

/**
 * The account the user is signed in to: its name, and what can be done to it. Two sections on one
 * screen and not two routes, which would be two guards and two headings for two forms nobody
 * navigates between (specification 2026-09-22, decisions A and H).
 */
export function Account() {
  const me = useMe()
  const signOutEverywhere = useSignOutEverywhere()

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 p-4">
      {/* The name is the heading and no section of its own: `UserOutputDto` holds nothing else. */}
      <AppHeader heading={me.data?.name ?? m.account()}>
        <AppNav />
      </AppHeader>
      <PasswordForm />
      <section className="flex flex-col items-start gap-2">
        <h3 className="text-lg font-semibold">{m.account_danger()}</h3>
        <p className="text-muted">{m.session_ends_note()}</p>
        <Button
          variant="danger"
          isDisabled={signOutEverywhere.isPending}
          onPress={() =>
            signOutEverywhere.mutate(undefined, {
              onError: () => toast.danger(m.sign_out_everywhere_refused()),
            })
          }
        >
          {m.sign_out_everywhere()}
        </Button>
        <DeleteAccount />
      </section>
    </main>
  )
}
