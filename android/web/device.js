/* Android TV (Philips) device layer. Replaces app/device.js inside the Android build.
   Talks only to the local Java bridge (window.CleanFrameAndroid). No network, no credentials. */
(function () {
  'use strict';
  var PS5_PORT = 1;
  var PACKAGES = {
    netflix: ['com.netflix.ninja', 'com.netflix.mediaclient'],
    youtube: ['com.google.android.youtube.tv', 'com.google.android.youtube.tvunplugged']
  };
  var INPUT = 'input.hdmi.';
  /* Tizen IDs saved by the upstream catalogue are translated, so older preferences still launch. */
  var LEGACY = { 'org.tizen.netflix-app': 'netflix', '9Ur5IzDKqV.TizenYouTube': 'youtube' };

  window.CleanFramePlatform = {
    name: 'Philips',
    art: false,
    primaryApps: [
      { id: 'netflix', name: 'Netflix', mark: true, appId: PACKAGES.netflix[0] },
      { id: 'youtube', name: 'YouTube', mark: true, appId: PACKAGES.youtube[0] },
      { id: 'ps5', name: 'PS5', mark: true, appId: INPUT + PS5_PORT }
    ]
  };

  function bridge() { return window.CleanFrameAndroid; }
  function unavailable(done) {
    done({ message: 'Open CleanFrame on the TV to use this control.', detail: 'The web preview cannot control the TV.' });
  }
  function parse(text, fallback) {
    try { return JSON.parse(text); } catch (err) { return fallback; }
  }
  /* Java calls are synchronous; defer the callback so the UI keeps its normal async flow. */
  function call(done, run) {
    if (!bridge()) { unavailable(done); return; }
    setTimeout(function () {
      var error = '';
      try { error = run(bridge()) || ''; } catch (err) { error = String(err && err.message || err); }
      if (error) done({ message: 'The TV could not open this screen.', detail: error });
      else done(null);
    }, 0);
  }
  function installed(packages) {
    var b = bridge(), i;
    for (i = 0; i < packages.length; i++) if (b.isInstalled(packages[i])) return packages[i];
    return '';
  }
  function launchApp(id, done) {
    var brand = LEGACY[id] || '', port;
    if (typeof id === 'string' && id.indexOf(INPUT) === 0) {
      port = Number(id.slice(INPUT.length));
      setSource(port, function (err) { done(err || null); });
      return;
    }
    if (!bridge()) { unavailable(done); return; }
    Object.keys(PACKAGES).forEach(function (key) { if (PACKAGES[key].indexOf(id) >= 0) brand = key; });
    if (brand) {
      id = installed(PACKAGES[brand]);
      if (!id) { done({ message: (brand === 'netflix' ? 'Netflix' : 'YouTube') + ' is not installed on this TV.', detail: 'Install it from Google Play, then try again.' }); return; }
    }
    call(done, function (b) { return b.launch(id); });
  }
  function launch(brand, done) {
    if (!PACKAGES[brand]) { done({ message: 'This app has no launch target.' }); return; }
    launchApp(PACKAGES[brand][0], done);
  }
  function art(done) { done({ message: 'Art Mode is a Samsung feature.', detail: 'It is not available on this TV.' }); }
  function home(done) { call(done, function (b) { return b.openSystemHome(); }); }
  function settings(done) { call(done, function (b) { return b.openSettings(); }); }

  function sources(done) {
    if (!bridge()) { unavailable(done); return; }
    setTimeout(function () {
      var list = parse(bridge().listInputs(), null);
      if (!Array.isArray(list)) { done({ message: 'The TV did not list its inputs.' }); return; }
      done(null, list.map(function (i) {
        return { number: i.number, name: i.name || 'HDMI ' + i.number, connected: !!i.connected, active: false };
      }));
    }, 0);
  }
  function setSource(port, done) {
    if (typeof port !== 'number' || !isFinite(port) || port < 1 || Math.floor(port) !== port) { done({ message: 'This HDMI input is not available.' }); return; }
    if (!bridge()) { unavailable(done); return; }
    setTimeout(function () {
      var error = '';
      try { error = bridge().openInput(port) || ''; } catch (err) { error = String(err && err.message || err); }
      if (error) done({ message: 'The TV could not switch to HDMI ' + port + '.', detail: error + ' Use the remote’s Sources button instead.' });
      else done(null, { number: port, name: 'HDMI ' + port });
    }, 0);
  }
  function cancelSource() {}

  function apps(done) {
    if (!bridge()) { unavailable(done); return; }
    setTimeout(function () {
      var list = parse(bridge().listApps(), null), result = [], brands = {};
      if (!Array.isArray(list)) { done({ message: 'Could not read installed apps.' }); return; }
      Object.keys(PACKAGES).forEach(function (key) { PACKAGES[key].forEach(function (p) { brands[p] = key; }); });
      list.forEach(function (a) {
        if (!a || typeof a.id !== 'string' || !a.name) return;
        var brand = brands[a.id] || '';
        if (brand && result.some(function (r) { return r.brand === brand; })) return;
        result.push({ appId: a.id, name: a.name, brand: brand, icon: a.icon || '', rank: brand ? 0 : 30 });
      });
      result.push({ appId: INPUT + PS5_PORT, name: 'PS5', brand: 'ps5', icon: '', rank: 1 });
      result.sort(function (a, b) { return a.rank - b.rank || a.name.toLowerCase().localeCompare(b.name.toLowerCase()); });
      done(null, result);
    }, 0);
  }

  /* The Java side calls this for the remote's Back button. */
  window.CleanFrameKey = function (code) {
    function send(type) {
      var ev = document.createEvent('Event');
      ev.initEvent(type, true, true);
      try { Object.defineProperty(ev, 'keyCode', { get: function () { return code; } }); } catch (err) { ev.keyCode = code; }
      (document.activeElement || document).dispatchEvent(ev);
    }
    send('keydown'); send('keyup');
  };

  window.FrameDevice = { launch: launch, launchApp: launchApp, art: art, home: home, settings: settings, apps: apps, sources: sources, setSource: setSource, cancelSource: cancelSource };
}());
