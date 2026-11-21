/** New-order alert: a short beep pattern via WebAudio + optional browser notification. */
let ctx: AudioContext | null = null;

export function unlockAudio() {
  if (!ctx) ctx = new AudioContext();
  if (ctx.state === "suspended") void ctx.resume();
}

export function playAlert() {
  try {
    unlockAudio();
    const c = ctx!;
    [0, 0.35, 0.7].forEach((t) => {
      const o = c.createOscillator();
      const g = c.createGain();
      o.frequency.value = 880;
      o.type = "square";
      g.gain.setValueAtTime(0.2, c.currentTime + t);
      g.gain.exponentialRampToValueAtTime(0.001, c.currentTime + t + 0.25);
      o.connect(g).connect(c.destination);
      o.start(c.currentTime + t);
      o.stop(c.currentTime + t + 0.3);
    });
  } catch {
    /* audio unavailable */
  }
}

export function browserNotify(title: string, body: string) {
  if (!("Notification" in window)) return;
  if (Notification.permission === "granted") {
    new Notification(title, { body, icon: "./favicon.svg" });
  }
}

export async function requestNotifyPermission() {
  if ("Notification" in window && Notification.permission === "default") {
    await Notification.requestPermission();
  }
}
