import { Button, toast } from "@heroui/react"
import { AppHeader } from "../components/AppHeader"
import { AppNav } from "../components/AppNav"
import { useMe } from "../me"
import { m } from "../paraglide/messages.js"
import { useSignOutEverywhere } from "../session"

/**
 * The account the user is signed in to: its name, and what can be done to it. The dangerous part
 * is its own section, and block 20 adds the password form above it and the deletion beside the
 * control below (specification 2026-09-22, decisions A and H).
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
      <section className="flex flex-col items-start gap-2">
        <h3 className="text-lg font-semibold">{m.account_danger()}</h3>
        {/* The consequence before the gesture, not after it (decision C). */}
        <p className="text-muted">{m.sign_out_everywhere_note()}</p>
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
      </section>
    </main>
  )
}
