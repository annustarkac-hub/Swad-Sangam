/** Bridge to the Swad Sangam Admin / Staff Android apps (WebView). In a normal browser it is absent and the web behaviour is used. */
interface AppBridge {
  printHtml(title: string, html: string): void; printPage(title: string): void; saveText(filename: string, mime: string, text: string): void; openExternal(url: string): void;
  /** Added in app 1.2: native high-priority notification with sound. */
  notify?(title: string, body: string): void;
}
export const appBridge = (): AppBridge | null => ((window as unknown as { SwadSangamApp?: AppBridge }).SwadSangamApp ?? null);

/** window.print() that also works inside the Android app. */
export function printPage() {
  const b = appBridge();
  if (b) b.printPage(document.title || "Swad Sangam"); else window.print();
}

/** Opens a link in a new tab (browser) or in the phone's viewer (Android app). */
export function openExternal(url: string) {
  const b = appBridge();
  if (b) b.openExternal(url); else window.open(url, "_blank");
}
