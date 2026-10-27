// Pochi notch — M0 prototype
// Jam + tanggal, dan titik halaman (placeholder).

function pad(n: number): string {
  return n.toString().padStart(2, "0");
}

function tick() {
  const now = new Date();
  const h = now.getHours();
  const m = now.getMinutes();
  const clock = document.querySelector<HTMLElement>("#clock");
  const dateEl = document.querySelector<HTMLElement>("#date");
  const ampm = h >= 12 ? "PM" : "AM";
  const h12 = h % 12 === 0 ? 12 : h % 12;
  if (clock) clock.textContent = `${pad(h12)}:${pad(m)} ${ampm}`;
  if (dateEl) {
    dateEl.textContent = now.toLocaleDateString("id-ID", {
      weekday: "short",
      day: "numeric",
      month: "short",
    });
  }
}

function buildDots(n = 7) {
  const dots = document.querySelector<HTMLElement>("#dots");
  if (!dots) return;
  for (let i = 0; i < n; i++) {
    const s = document.createElement("span");
    dots.appendChild(s);
  }
}

window.addEventListener("DOMContentLoaded", () => {
  buildDots();
  tick();
  setInterval(tick, 1000);
});
