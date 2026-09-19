const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { runInNewContext } = require('node:vm');
const { resolve } = require('node:path');

const html = readFileSync(resolve(__dirname, '../../main/assets/map/index.html'), 'utf8');
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];

function preview(search = '?marker=17.3616,78.4867') {
  const handlers = {};
  const window = {};
  let images = [];
  let center, marker;
  const layer = { on(event, callback) { handlers[event] = callback; }, addTo() {} };
  const map = { setView(position) { center = position; return this; }, attributionControl: { setPrefix() {} } };
  runInNewContext(script, {
    window, location: { search }, URLSearchParams,
    document: { querySelectorAll: () => images, getElementById: () => ({}) },
    ResizeObserver: class { observe() {} },
    L: {
      map: () => map, tileLayer: () => layer, divIcon: options => options,
      marker(position) { marker = position; return { addTo() {} }; }
    }
  });
  return { window, handlers, get center() { return Array.from(center); },
    get marker() { return Array.from(marker); }, setImages(value) { images = value; } };
}

test('the selected destination centers the map and pin, but is not ready before tiles load', () => {
  const map = preview();
  assert.deepEqual(map.center, [17.3616, 78.4867]);
  assert.deepEqual(map.marker, map.center);
  assert.equal(map.window.rideSaathiMapStatus, 'loading');
});

test('only successfully decoded map tiles make the preview ready', () => {
  const map = preview();
  map.setImages([{ complete: true, naturalWidth: 256 }, { complete: true, naturalWidth: 256 }]);
  map.handlers.load();
  assert.equal(map.window.rideSaathiMapStatus, 'ready');
  map.handlers.loading();
  assert.equal(map.window.rideSaathiMapStatus, 'loading');
});

test('missing, failed, and incomplete tiles report a map loading error', () => {
  for (const images of [[], [{ complete: true, naturalWidth: 0 }],
    [{ complete: false, naturalWidth: 256 }],
    [{ complete: true, naturalWidth: 256 }, { complete: true, naturalWidth: 0 }]]) {
    const map = preview();
    map.setImages(images);
    map.handlers.load();
    assert.equal(map.window.rideSaathiMapStatus, 'error');
  }
});

test('an initialization failure is exposed instead of an indefinitely blank map', () => {
  assert.equal(preview('').window.rideSaathiMapStatus, 'error');
});
