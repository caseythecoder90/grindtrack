# Go-live: the partner, the chat, the pictures

The checklist for turning on everything the three chat pull requests brought — roles (#63), the
chat (#66) and pictures, video and stickers (#65) — and proving each piece on two phones. Do it
in order; each step names what "done" looks like.

## 0. Before you start

- All three PRs merged to `main` and the last deploy green (`Actions` on GitHub, or
  `kubectl -n grindtrack rollout status deploy/grindtrack`).
- The cluster repo's PRs (the four `MEDIA_S3_*` variables on the deployment and a 110 MB ingress
  body; then 768Mi of memory and 5-second probe timeouts) merged, and **applied** — CI deploys the
  image, not the manifests:

  ```bash
  cd ~/Projects/k8s-cluster-hetzner
  kubectl apply -k kubernetes/apps/grindtrack/overlays/prod
  ```

- The Hetzner bucket and its S3 key pair to hand (Cloud Console → the project → Object Storage; the
  keys from *Security → S3 credentials* of the **same project**). Prove the bucket exists before
  anything else — from your laptop, with no keys:

  ```bash
  curl -s https://<the bucket name>.nbg1.your-objectstorage.com/
  ```

  `AccessDenied` is the answer you want: the bucket is there and private. `NoSuchBucket` means the
  name or the location is wrong — or, as happened with the first bucket, the console lists a bucket
  the storage cluster never got (it showed under `ListBuckets` too, and answered 404 to everything
  else). Delete it in the console, create it again, and curl until it says `AccessDenied`.

All `kubectl` below assumes `KUBECONFIG` points at the cluster's admin config.

## 1. The migrations ran

The first boot after the merge runs 038 (roles), 039 (the chat) and 040 (pictures). Look for
them and for no validation error:

```bash
kubectl -n grindtrack logs deploy/grindtrack --since=30m | grep -E "038-|039-|040-|Liquibase|validat"
```

Done looks like: three `ChangeSet … ran successfully` lines and the app listening. A
`SchemaManagementException` here means a migration did not run — stop and say so.

## 2. The bucket is on

```bash
kubectl -n grindtrack patch secret grindtrack-secrets --type=merge -p '{"stringData":{
  "MEDIA_S3_ENDPOINT":"https://nbg1.your-objectstorage.com",
  "MEDIA_S3_BUCKET":"<the bucket name>",
  "MEDIA_S3_ACCESS_KEY":"<access key>",
  "MEDIA_S3_SECRET_KEY":"<secret key>"
}}'
kubectl -n grindtrack rollout restart deploy/grindtrack
kubectl -n grindtrack rollout status deploy/grindtrack
```

Then, signed in on the laptop, open `/api/chat/media/status` in a tab. Done looks like
`{"configured":true,"maxBytes":104857600,"bucket":"ok"}` — `ok` means the app asked the bucket
with those keys and it answered. `configured: false` means a variable did not reach the pod: the
secret key names above, the manifest not applied (step 0), or the restart not done. Anything else
in `bucket` is the bucket's own answer: `NoSuchBucket (404)` is step 0's curl again (the name, the
location, or a bucket the console lists that the cluster does not have); `AccessDenied` or
`SignatureDoesNotMatch (403)` is the key pair, or keys from a different project than the bucket.
The same sentence is in the log at boot:

```bash
kubectl -n grindtrack logs deploy/grindtrack | grep "Media:"
```

## 3. The partner account

On the laptop, signed in as you: **Accounts** (header button; on the phone it is in the more
sheet) → *add a partner*: her username and a password of 12+ characters. The screen shows her
authenticator key **once**. Open her authenticator app, add an account by key, type it in (or
paste the `otpauth://` line into any QR generator and scan that). Press *done — hide it*.

If the key is lost before it is in her app: *sign out everywhere* on her row does not help —
there is no way to show it again. Create a second account with a different name and use that.

## 4. Her phone

1. Safari → `https://track.caseyrquinn.com` → share → **Add to Home Screen** (notifications need
   the installed app on an iPhone).
2. Open it from the home screen → **Log in** → her username, password and the code; tick *trust
   this device for 30 days*.
3. Done looks like: the room, with "with casey" at the top, and nothing else — no tabs. If she
   sees your tabs, stop: that is the one thing that must not happen (and cannot, unless the roles
   migration did not run).
4. *settings* (above the room) → **turn on notifications** → allow → **send a test**. Done
   looks like a notification on her lock screen.

## 5. Your phone

The installed app → the more sheet → `chat`. Notifications were already on for you; if not,
same as above.

## 6. The test, in order

Each line is something to see on *both* phones unless it says otherwise.

| # | Do | See |
|---|---|---|
| 1 | You: send "hi" | It appears on hers within a second; your bubble says *delivered*, then *seen* once she has it on screen |
| 2 | Her: start typing, do not send | Your screen says *typing…* |
| 3 | Her: send a reply | Your unread mark clears as it arrives while you are on the chat; leave the chat first and it shows a count on the tab and a dot on the more button |
| 4 | Her: put the phone down (app in the background); you: send another | Her lock screen shows "casey: …" within about five seconds — the push that fires only when the open socket did not deliver it |
| 5 | Tap a message → ❤️ | The heart under it on both phones; tap it again and it goes |
| 6 | Send "🎉🎉" | Big, no bubble |
| 7 | Tap the camera → a photo → *send* | *uploading …%*, then the picture in your bubble and hers; tap it for full size |
| 8 | The camera → a short video | Same, with a poster frame; plays on tap |
| 9 | Tap a picture → *keep as sticker* | The sticker button (the smiling square) → the tray shows it; tap it → sent, small, no bubble |
| 10 | Tap one of your own messages → *unsend* | Both phones show *unsent* in its place; a picture's bytes are gone from the bucket |
| 11 | Turn on airplane mode on one phone, send from the other, turn it off | The message arrives when the socket comes back, without a reload |

## 7. When something does not happen

```bash
kubectl -n grindtrack logs deploy/grindtrack --since=15m | grep -E "Chat socket|Chat push|Media|Push"
```

| Symptom | Where to look |
|---|---|
| Message sent, never arrives on the other phone | `Chat socket … opened for <name>` lines — is her socket there? If not, the ingress or a stale app; reload the app |
| No push on the lock screen | `Chat push to account 2: 0 sent` — her subscription is gone (turn notifications off and on); `Push … answered 4xx` — the push service refused, the reason is in the line |
| Upload fails with a sentence about the bucket | Try once more first. On the first evening a fresh bucket took one picture, then refused writes for two minutes (00:33–00:35 UTC, some pictures and every thumbnail), then took everything again — the keys, the clock and the bucket all checked out from inside the cluster afterwards. If it keeps failing, `could not store that: <code> (<status>) — …` names the S3 error code and where it points: `NoSuchBucket (404)` the bucket itself (step 0's curl, then step 2's status); `AccessDenied (403)` the bucket's policy or the key pair; `InvalidAccessKeyId` or `SignatureDoesNotMatch (403)` the key pair, from the bucket's project; `AuthorizationHeaderMalformed` the region (`MEDIA_S3_REGION`, default `nbg1`, must be the bucket's location). The same line is in the log as `Media upload by account … failed`; a refused thumbnail alone is `Media poster … was refused` and the picture still goes through |
| The app restarted during the test (`kubectl -n grindtrack get pods` shows a RESTART; `get events` says *liveness probe failed*) | Memory. The container is a JVM inside a fixed limit; the first use of the S3 client during an upload pushed it to the edge once, and the kernel's reclaim stalled it past a 1-second probe. The manifest now gives it 768Mi and 5-second probe timeouts; if it recurs, compare `kubectl -n grindtrack exec deploy/grindtrack -- cat /sys/fs/cgroup/memory.current` with the limit |
| A picture shows as a broken image | The signed link expired while the page sat, and the retry did not renew; pull to reload. If it is every picture, `/api/chat/media/status` |
| A video will not play on the laptop | An iPhone records HEVC; Chrome on Windows may not play it. On the phone: Settings → Camera → Formats → *Most Compatible* |
| She can see more than the room | Stop and say so. `SecurityConfigTest` guards this; if it passed and she sees tabs, the deploy is not what merged |

## 8. After it works

- The nightly Postgres backup is still the open item ([deployment.md](deployment.md#backups--currently-a-gap)),
  and now there are two people's messages in that database. The bucket is durable on its own; the
  rows that point at it are not backed up until that job exists.
- `chat` sits in the more sheet. If it becomes a daily tab, promoting it is one line in
  `frontend/src/lib/tabs.ts` (`PRIMARY_TABS`).
