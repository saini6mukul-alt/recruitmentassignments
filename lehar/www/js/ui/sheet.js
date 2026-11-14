// Stack of overlays (player, full sheets, popovers) so Android back closes the top one.
import { $ } from './dom.js';

const stack = [];

export function open(el, { onClose, pop = false } = {}) {
  if (stack.some((s) => s.el === el)) return;
  el.hidden = false;
  el.classList.remove('closing');
  if (pop) $('#scrim').hidden = false;
  stack.push({ el, onClose, pop });
}

export function close(el) {
  const i = stack.findIndex((s) => s.el === el);
  if (i < 0) return false;
  const [s] = stack.splice(i, 1);
  el.classList.add('closing');
  setTimeout(() => { el.hidden = true; el.classList.remove('closing'); }, 240);
  if (s.pop && !stack.some((x) => x.pop)) $('#scrim').hidden = true;
  s.onClose?.();
  return true;
}

export function closeTop() {
  const top = stack[stack.length - 1];
  return top ? close(top.el) : false;
}

export const isOpen = (el) => stack.some((s) => s.el === el);

export function initScrim() {
  $('#scrim').addEventListener('click', () => {
    const top = [...stack].reverse().find((s) => s.pop);
    if (top) close(top.el);
  });
}

/** Simple action popover: items = [{label, sub?, icon?, onClick}] */
export function menu(title, items) {
  const el = $('#popover');
  el.innerHTML = `<div class="grabber"></div><div class="sheet-head"><h2>${title}</h2></div><div class="sheet-body"></div>`;
  const body = el.querySelector('.sheet-body');
  for (const it of items) {
    const b = document.createElement('button');
    b.className = 'menu-item';
    b.innerHTML = `${it.icon ? `<span class="icon-btn" style="width:24px;height:24px">${it.icon}</span>` : ''}<span>${it.label}</span>${it.sub ? `<small>${it.sub}</small>` : ''}`;
    b.addEventListener('click', () => { close(el); it.onClick?.(); });
    body.appendChild(b);
  }
  open(el, { pop: true });
  return el;
}
