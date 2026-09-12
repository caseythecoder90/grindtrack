import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import {
  deviceLabel,
  pushState,
  subscribePush,
  unsubscribePush,
  type PushStatus,
} from "../../lib/push";
import {
  deletePushDevice,
  getPushStatus,
  listPushDevices,
  sendPushTest,
  savePushSubscription,
  type PushDevice,
  type PushServerStatus,
} from "./pushApi";

/**
 * Whether this device gets told things, and a button to prove it does.
 *
 * <p>Five states, one sentence or one button each. The important rule is in `turnOn`: the
 * permission prompt has to be the first thing the tap does, so the server's public key is fetched
 * on mount and nothing is awaited before `subscribePush`. Where push cannot work at all the panel
 * renders nothing, because a control that can only fail is worse than no control.
 */
export default function NotificationsPanel() {
  const [server, setServer] = useState<PushServerStatus | null>(null);
  const [local, setLocal] = useState<PushStatus | null>(null);
  const [devices, setDevices] = useState<PushDevice[]>([]);
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState("");

  const refresh = useCallback(async () => {
    const [remote, here] = await Promise.all([getPushStatus().catch(() => null), pushState()]);
    setServer(remote);
    setLocal(here);
    if (remote && remote.devices > 0) {
      setDevices(await listPushDevices().catch(() => []));
    } else {
      setDevices([]);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  if (!local || local.state === "unsupported") return null;

  async function turnOn() {
    if (!server?.publicKey) {
      setNote("push is off on the server — the runbook in docs/push-notifications.md turns it on");
      return;
    }
    setBusy(true);
    setNote("");
    try {
      // First await inside is the permission prompt: nothing may come before it.
      const subscription = await subscribePush(server.publicKey);
      await savePushSubscription(subscription, deviceLabel());
      setNote("on — send a test to be sure it arrives");
      await refresh();
    } catch (e) {
      setNote(errorMessage(e, "could not turn notifications on"));
    } finally {
      setBusy(false);
    }
  }

  async function turnOff() {
    setBusy(true);
    setNote("");
    try {
      // Server first: a device that loses the network here is a row that will 410 and clean
      // itself up, rather than a phone with a subscription nobody knows about.
      const mine = devices.find((d) => d.endpoint === local?.endpoint);
      if (mine) await deletePushDevice(mine.id);
      await unsubscribePush();
      setNote("off");
      await refresh();
    } catch (e) {
      setNote(errorMessage(e, "could not turn notifications off"));
    } finally {
      setBusy(false);
    }
  }

  async function test() {
    setBusy(true);
    setNote("");
    try {
      const outcome = await sendPushTest(local?.endpoint ?? null);
      setNote(
        outcome.sent > 0
          ? "sent — it should arrive in a few seconds"
          : outcome.gone > 0
            ? "this device's subscription had lapsed; turn notifications on again"
            : "the push service did not accept it — try again in a minute",
      );
      if (outcome.gone > 0) await refresh();
    } catch (e) {
      setNote(errorMessage(e, "could not send a test"));
    } finally {
      setBusy(false);
    }
  }

  async function remove(device: PushDevice) {
    setBusy(true);
    try {
      await deletePushDevice(device.id);
      await refresh();
    } catch (e) {
      setNote(errorMessage(e, "could not remove that device"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="notif">
      <div className="row">
        <span className="label">notifications</span>
        <span className="state">
          {local.state === "on"
            ? "on · this device"
            : local.state === "off"
              ? "off"
              : local.state === "blocked"
                ? "blocked"
                : "needs the installed app"}
        </span>
      </div>

      {local.state === "needs-install" && (
        <p className="hint">
          on an iPhone, notifications need the installed app: share → add to home screen, then
          come back here.
        </p>
      )}
      {local.state === "blocked" && (
        <p className="hint">
          allow notifications for grindtrack in the browser's settings, then reopen this.
        </p>
      )}
      {local.state === "off" && (
        <div className="row">
          <button type="button" className="primary" onClick={turnOn} disabled={busy}>
            turn on notifications
          </button>
          {server && !server.configured && <span className="hint">push is off on the server</span>}
        </div>
      )}
      {local.state === "on" && (
        <div className="row">
          <button type="button" onClick={test} disabled={busy}>
            send a test
          </button>
          <button type="button" onClick={turnOff} disabled={busy}>
            turn off
          </button>
        </div>
      )}

      {devices.length > 0 && (
        <ul className="devices">
          {devices.map((d) => {
            const mine = d.endpoint === local.endpoint;
            return (
              <li key={d.id}>
                <span>
                  {d.label ?? "a device"}
                  {mine && " · this device"} · added {d.createdAt.slice(0, 10)}
                </span>
                {!mine && (
                  <button type="button" className="linkish" onClick={() => remove(d)} disabled={busy}>
                    remove
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      )}

      {note && <p className="hint">{note}</p>}
    </div>
  );
}
