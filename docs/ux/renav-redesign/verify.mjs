import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { runInNewContext } from 'node:vm';

const source = readFileSync(new URL('./prototype.js', import.meta.url), 'utf8');
const tokens = readFileSync(new URL('./tokens.css', import.meta.url), 'utf8');
const page = readFileSync(new URL('./index.html', import.meta.url), 'utf8');
const objectSource = source.match(/const copy = (\{[\s\S]*?\n\};)/)?.[1];
const screensSource = source.match(/const screens = (\[[\s\S]*?\n\];)/)?.[1];
if (!objectSource || !screensSource) throw new Error('Prototype contract not found');
const copy = runInNewContext(`(${objectSource.slice(0, -1)})`);
const screens = runInNewContext(screensSource.slice(0, -1));
const english = Object.keys(copy.en).sort();
const ukrainian = Object.keys(copy.uk).sort();
if (english.join('|') !== ukrainian.join('|')) throw new Error('EN/UK resource keys differ');
for (const screen of screens) {
  if (!copy.en[screen] || !copy.uk[screen]) throw new Error(`Missing screen label: ${screen}`);
}
for (const key of ['manualNotMeasured','low','uncertainty','positionLost','progressBody','incidentConsent']) {
  if (!copy.en[key] || !copy.uk[key]) throw new Error(`Missing safety label: ${key}`);
}
for (const role of ['--navy','--ready','--caution','--danger','--ink','--surface','--focus']) {
  if (!tokens.includes(`${role}:`)) throw new Error(`Missing design token: ${role}`);
}
if (!page.includes('prototype.js') || !page.includes('tokens.css')) throw new Error('Prototype asset link missing');
const assets = {
  'historical-map-backdrop.png': 'E6AAF8C169272B3E7EDEE303370F29FD49F8191E159D837E5F77CECF4D9B7139',
  'historical-route-backdrop.png': 'EBD8F1D208A54EF1F71F4D25AE77C193C0E09D9DE8D0C445BA64EDF40CD3ED52'
};
for (const [file, expected] of Object.entries(assets)) {
  const actual = createHash('sha256').update(readFileSync(new URL(`./${file}`, import.meta.url))).digest('hex').toUpperCase();
  if (actual !== expected) throw new Error(`Historical asset changed: ${file}`);
}
function luminance(hex) {
  const rgb = hex.match(/[0-9a-f]{2}/gi).map(v => parseInt(v,16)/255).map(v => v <= 0.04045 ? v/12.92 : ((v+0.055)/1.055)**2.4);
  return 0.2126*rgb[0] + 0.7152*rgb[1] + 0.0722*rgb[2];
}
function contrast(a,b) {
  const x=luminance(a),y=luminance(b);
  return (Math.max(x,y)+0.05)/(Math.min(x,y)+0.05);
}
const contrastPairs = [
  ['12355B','FFFFFF'],['067463','DFF5EC'],['A45A00','FFF0D4'],['B3261E','FCE8E6'],
  ['9BC9FF','101418'],['7BDCC3','153E38'],['FFCF80','49300D'],['FFB4AB','56241F']
];
for (const [fg,bg] of contrastPairs) if (contrast(fg,bg) < 4.5) throw new Error(`Text contrast below 4.5:1: ${fg}/${bg}`);
const elements = new Map();
function element(id) {
  if (!elements.has(id)) elements.set(id, {
    value: '', innerHTML: '', dataset: {}, style: { values:{}, setProperty(key,value) { this.values[key]=value; } },
    addEventListener() {}, querySelectorAll() { return []; }, querySelector() { return null; }
  });
  return elements.get(id);
}
const context = {
  document: { getElementById: element, documentElement: { lang: 'en', dataset: {} } },
  location: { href: 'http://design.invalid/' },
  history: { replaceState() {} }, URL, URLSearchParams
};
runInNewContext(`${source}\nglobalThis.__test = { show: (s, lang, mode='light', textScale='1') => { state=s; language=lang; theme=mode; scale=textScale; render(); return app.innerHTML; }, position: (s) => positionClass(s), retainFrom: (s) => { retainedPosition = positionClass(s); }, setSelected: (value) => { selectedDestination = value; }, resolve: (from,to) => resolveUserTransition(from,to) };`, context);
const heading = {
  ready:'offlineReady',preparing:'preparingTitle',repair:'repairTitle',unavailable:'unavailableTitle',
  search:'searchTitle',needsOrigin:'needsOriginTitle',manual:'manualTitle',preview:'previewTitle',
  guidance:'guidanceTitle',degraded:'degradedTitle',lost:'lostTitle',progressHold:'progressTitle',
  arrival:'arrivalTitle',menu:'menu',advanced:'advancedTitle',incident:'incidentTitle',savedReports:'savedTitle',
  permissions:'permissionsTitle',searchEmpty:'searchEmptyTitle',routeError:'routeErrorTitle',paused:'pausedTitle',
  background:'backgroundTitle',departure:'departureTitle',recovering:'recoveringTitle',restart:'restartTitle',
  incidentDelete:'incidentDeleteTitle',incidentFailure:'incidentFailureTitle',
  permissionDenied:'permissionDeniedTitle',routeCalculating:'routeCalculatingTitle',destinationRetained:'destinationRetainedTitle',
  partialOffline:'partialOfflineTitle',offlineNetwork:'offlineNetworkTitle',active:'activeTitle',freeSpace:'freeSpaceTitle',arrivalPending:'arrivalPendingTitle'
};
for (const lang of ['en','uk']) for (const screen of screens) {
  const html = context.__test.show(screen, lang);
  if (!html.includes(copy[lang][heading[screen]])) {
    throw new Error(`Missing rendered state text: ${lang}/${screen}`);
  }
  if (!html || html.length < 100) throw new Error(`Empty state: ${lang}/${screen}`);
}
for (const lang of ['en','uk']) {
  for (const [screen,key] of [['needsOrigin','manualNotMeasured'],['preview','uncertainty'],['guidance','manualSource'],['lost','noProgress'],['incident','incidentConsent'],['paused','pausedBody'],['departure','noProgress'],['recovering','noProgress'],['restart','restartBody'],['incidentDelete','incidentDeleteBody']]) {
    if (!context.__test.show(screen,lang).includes(copy[lang][key])) throw new Error(`Safety text absent: ${lang}/${screen}/${key}`);
  }
}
const app = element('app');
for (const lang of ['en','uk']) for (const mode of ['light','dark']) for (const textScale of ['1','1.5','2']) for (const screen of screens) {
  const html = context.__test.show(screen,lang,mode,textScale);
  if (!html.includes(copy[lang][heading[screen]]) || app.dataset.theme !== mode || context.document.documentElement.dataset.theme !== mode || app.style.values['--scale'] !== textScale) {
    throw new Error(`Variant contract failed: ${lang}/${mode}/${textScale}/${screen}`);
  }
}
const shown = (screen) => context.__test.show(screen,'en');
for (const state of ['preparing','repair','partialOffline','offlineNetwork']) {
  if (!shown(state).includes('aria-disabled="true"') || shown(state).includes(copy.en.offlineReady)) throw new Error(`Premature offline readiness: ${state}`);
}
for (const [state,next] of [['needsOrigin','destinationRetained'],['manual','routeCalculating'],['routeError','destinationRetained'],['routeCalculating','destinationRetained'],['preview','destinationRetained'],['paused','recovering'],['background','recovering']]) {
  if (!shown(state).includes(`data-next="${next}"`)) throw new Error(`Missing safe exit: ${state} → ${next}`);
}
for (const state of ['paused','progressHold','freeSpace','departure','recovering','lost','arrivalPending']) {
  if (shown(state).includes(copy.en.nextTurn) || shown(state).includes('data-next="arrival"')) throw new Error(`Unsupported progress or arrival: ${state}`);
}
for (const state of screens) if (shown(state).includes('data-next="arrival"')) throw new Error(`User action triggers definitive arrival: ${state}`);
for (const state of ['progressHold','freeSpace','departure']) {
  if (!shown(state).includes(copy.en.positionReliable) || shown(state).includes(copy.en.positionLost)) throw new Error(`Ambiguity conflated with position loss: ${state}`);
}
for (const state of ['routeCalculating','routeError','preview','guidance','active','paused','background','progressHold','freeSpace','departure','recovering','degraded','lost','arrivalPending','arrival']) {
  if (!shown(state).includes(copy.en.manualSource) || !shown(state).includes(copy.en.uncertainty)) throw new Error(`Manual provenance not visible: ${state}`);
}
context.__test.retainFrom('active');
if (context.__test.position('paused') !== 'reliable' || context.__test.position('background') !== 'reliable') throw new Error('Pause/background changed position reliability');
context.__test.retainFrom('guidance');
if (context.__test.position('paused') !== 'unavailable') throw new Error('Pause promoted unavailable position');
context.__test.setSelected(false);
if (context.__test.resolve('search','searchCancel') !== 'ready') throw new Error('Empty search cancellation must return to map');
if (context.__test.resolve('search','needsOrigin') !== 'needsOrigin' || context.__test.resolve('search','searchCancel') !== 'destinationRetained') throw new Error('Selected destination lost on search cancellation');
if (!shown('ready').includes(copy.en.result)) throw new Error('Stop/map return hid valid retained destination');
if (shown('lost').includes('map-target') || shown('lost').includes(copy.en.nextTurn)) throw new Error('Unavailable positioning shows a precise target or directions');
if (shown('routeCalculating').includes('data-next="preview"') || shown('recovering').includes('data-next="active"')) throw new Error('User action bypasses verified route or position evidence');
if (shown('arrivalPending').includes(copy.en.arrivalTitle)) throw new Error('Pending arrival presented as definitive');
if (!tokens.includes('overflow-y: auto') || !tokens.includes('max-height: min(70%, 620px)') || !tokens.includes('[data-theme="dark"]') || !tokens.includes('@media (max-width: 720px)')) throw new Error('Responsive/theme structural support missing');
for (const lang of ['en','uk']) {
  context.__test.show('ready',lang);
  if (element('prototypeBadge').textContent !== copy[lang].prototypeBadge) throw new Error(`Missing simulated-map disclosure: ${lang}`);
}
for (const selector of ['.controls {','.controls p {','.controls select {','.controls .footnote {']) {
  const rule = tokens.match(new RegExp(selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[^}]*}'))?.[0];
  if (!rule || /#[0-9a-f]{3,8}\b/i.test(rule)) throw new Error(`Review control has a fixed-color rule: ${selector}`);
}
if (!tokens.includes('backdrop-filter: blur(24px) grayscale(1)') || !tokens.includes('--backdrop-veil-heavy:')) throw new Error('Historical controls are not visually neutralized');
console.log(`PASS: ${screens.length} states × 2 languages rendered; ${english.length} paired EN/UK keys; 7 semantic tokens; 8 text-contrast pairs; 2 unchanged historical assets.`);
console.log(`PASS: ${screens.length * 2 * 2 * 3} language/theme/text-scale render contracts; P0 transition and safety assertions. Visual overflow remains unverified.`);
