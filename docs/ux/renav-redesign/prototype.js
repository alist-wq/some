/* Design-only state board. No application service, storage, location, or network access. */
const screens = [
  'ready','preparing','repair','unavailable','search','needsOrigin','manual','preview',
  'guidance','degraded','lost','progressHold','arrival','menu','advanced','incident','savedReports',
  'permissions','permissionDenied','searchEmpty','routeError','routeCalculating','destinationRetained',
  'partialOffline','offlineNetwork','paused','background','active','departure','freeSpace','recovering',
  'arrivalPending','restart','incidentDelete','incidentFailure'
];
const copy = {
  en: {
    ready:'Ready map',preparing:'Preparing',repair:'Repair needed',unavailable:'Position unavailable',
    search:'Destination search',needsOrigin:'Route needs origin',manual:'Choose manual start',
    preview:'Route preview',guidance:'Guidance',degraded:'Degraded position',lost:'Location lost',
    progressHold:'Route progress uncertain',arrival:'Arrival',menu:'Menu',advanced:'Advanced',
    incident:'Report a problem',savedReports:'Saved reports',
    permissions:'Location permission',searchEmpty:'No search results',routeError:'Route unavailable',paused:'Guidance paused',background:'Guidance in background',departure:'Away from route',recovering:'Checking position',restart:'Session ended',incidentDelete:'Delete report',incidentFailure:'Report not saved',
    permissionDenied:'Location access off',routeCalculating:'Preparing route',destinationRetained:'Destination kept',partialOffline:'Map available',offlineNetwork:'Offline preparation paused',active:'Active guidance',freeSpace:'Route match unavailable',arrivalPending:'Near destination',
    permissionDeniedTitle:'Location access is off',permissionDeniedBody:'The offline map remains available. Choose a starting point to plan a route. Live progress waits until location access and reliable positioning are available.',openPermissionSettings:'Review location permission',
    partialOfflineTitle:'Map available; navigation not ready',partialOfflineBody:'This verified map can be explored. Routes and place search remain unavailable until their required offline data is verified.',exploreMap:'Explore available map',
    offlineNetworkTitle:'Preparation is waiting for a connection',offlineNetworkBody:'The required offline data could not finish downloading. Any already verified map remains available; navigation is not ready. Retry when a connection is available.',retryPreparation:'Retry preparation',
    destinationRetainedTitle:'Destination kept',destinationRetainedBody:'Your destination is still selected. Choose a starting point or change the destination when you are ready.',
    routeCalculatingTitle:'Preparing your walking route',routeCalculatingBody:'The destination remains selected. No guidance is active until the offline route is verified and you choose Start.',
    activeTitle:'Following the route',activeBody:'Turn and distance guidance is shown only while the current position and route progress are eligible.',nextTurn:'Next turn · about 80 m',
    freeSpaceTitle:'Route match unavailable',freeSpaceBody:'Your current position can remain reliable even when no route path can be matched. Route progress and arrival are paused until an eligible match returns.',
    arrivalPendingTitle:'Near your destination',arrivalPendingBody:'The route remains active. Arrival is not confirmed until fresh, independent and sufficiently certain positioning evidence supports it.',
    permissionsTitle:'Choose how location is used',permissionsBody:'Allow location for live guidance. Without it, you can still explore the offline map and choose a starting point. Background access is requested only when active guidance needs it.',allowLocation:'Review location access',notNow:'Not now',
    searchEmptyTitle:'No offline places found',searchEmptyBody:'Try another place name. Your selected destination is unchanged until you choose a new result.',trySearch:'Edit search',
    routeErrorTitle:'Could not prepare this route',routeErrorBody:'The destination stays selected. Check offline readiness or choose another starting point; no route has started.',tryRoute:'Try again',
    pausedTitle:'Guidance paused',pausedBody:'The route and destination remain. Progress and arrival will not update until you resume and a fresh reliable position is available.',
    backgroundTitle:'Guidance continues in background',backgroundBody:'Only an explicitly active navigation service can continue guidance. Return to the map to review its current reliability.',openNavigation:'Open navigation',
    departureTitle:'You may be away from the route',departureBody:'Your destination and planned route remain. Progress is held until the route can be matched reliably; no automatic route snap is claimed.',
    recoveringTitle:'Checking your position',recoveringBody:'A new location reading is being checked. Route progress resumes only after fresh, eligible evidence and a supported route match.',
    restartTitle:'Previous guidance is no longer active',restartBody:'After a new process starts, old confidence and route progress are not treated as current. Choose a destination or wait for a new reliable position.',
    incidentDeleteTitle:'Delete this saved report?',incidentDeleteBody:'Only this report will be removed. Cancel keeps it on this device.',confirmDelete:'Delete this report',
    incidentFailureTitle:'Report was not saved',incidentFailureBody:'The report remains unsaved. Try again only after reviewing the current information and confirming consent.',
    menuButton:'Menu',where:'Where to?',offlineReady:'Offline navigation ready',
    positionUnavailable:'Location unavailable',positionReliable:'Location reliable',
    positionEstimated:'Location estimated',positionLost:'Location lost',
    preparingTitle:'Preparing offline navigation',preparingBody:'Maps, routes and places are being checked. Navigation will be available when all required data is verified.',
    repairTitle:'Offline navigation needs repair',repairBody:'Required offline data is missing or could not be verified. Navigation is not ready yet.',
    repairAction:'Repair offline data',unavailableTitle:'You can still explore the map',
    unavailableBody:'Your current location is unavailable. Search for a destination or choose a starting point when planning a route.',
    searchTitle:'Find a destination',searchHint:'Search offline places',searchSub:'Kharkiv offline places',
    result:'10 Sumska',resultType:'Street address · offline result',cancel:'Cancel',back:'Back',
    needsOriginTitle:'Choose a starting point',needsOriginBody:'Your current location is unavailable. Choose a point on the map to plan this route.',
    to:'To',manualNotMeasured:'A point you choose on the map is not a measured location.',
    chooseStart:'Choose starting point',changeDestination:'Change destination',
    manualTitle:'Place the starting point',manualBody:'Move the map until the chosen point is under the marker, then confirm.',
    chosenStart:'Chosen start',
    confirmManual:'Confirm starting point',manualSource:'Starting point chosen on the map',
    low:'Low confidence',uncertainty:'Manual-start uncertainty: ±100 m',estimatedUncertainty:'Current-position uncertainty must come from the current eligible estimate.',
    previewTitle:'Review your walking route',routeDistance:'About 310 m',routeKind:'Offline pedestrian route',
    startGuidance:'Start guidance',previewNote:'Guidance waits for a reliable current location. This chosen start is not a measured position.',
    guidanceTitle:'Waiting for a reliable location',guidanceBody:'The route stays visible. Turn-by-turn progress waits for a reliable current location.',
    pause:'Pause guidance',resume:'Resume guidance',stop:'Stop navigation',
    degradedTitle:'Location is estimated',degradedBody:'Your position is less certain. Follow the map cautiously; precise route progress is paused when unsupported.',
    lostTitle:'Location unavailable',lostBody:'No reliable current position is available. The route and destination remain visible, but progress and arrival are not claimed.',
    progressTitle:'Route progress uncertain',progressBody:'Your current position remains available, but the route match is uncertain. Turn-by-turn progress is paused until it is supported again.',
    arrivalTitle:'Destination reached',arrivalBody:'Arrival is shown only after fresh, integrity-eligible and sufficiently accurate positioning evidence.',
    done:'Done',navigation:'Navigation',advancedLink:'Advanced',reportProblem:'Report a problem',savedLink:'Saved problem reports',
    advancedTitle:'Advanced tools',advancedBody:'Technical details and training tools stay separate from everyday navigation.',
    diagnostics:'Diagnostics',trainer:'Trainer',offlineDetails:'Offline data details',recordings:'Recordings',
    incidentTitle:'Review a problem report',incidentBody:'This report contains recent navigation-state categories and reliability information, not your coordinates or route history.',
    incidentEvent:'Position unavailable · route progress paused',incidentConsent:'I reviewed this information and agree to save this report on this device.',
    incidentSave:'Save on this device',incidentCancel:'Do not save',savedTitle:'Saved problem reports',
    savedBody:'Reports remain on this device until you delete them. Sharing is not available in this MVP.',
    savedExample:'Example report · navigation reliability',delete:'Delete report',
    offline:'Offline',position:'Position',manualLabel:'Manual start',confidence:'Confidence',
    route:'Route',selected:'Selected destination',noReady:'Not ready',noProgress:'Progress paused',
    returnMap:'Return to map',unverified:'Design state only — not a live positioning claim',
    prototypeBadge:'Simulated design · historical map image, not live navigation'
  },
  uk: {
    ready:'Мапа готова',preparing:'Підготовка',repair:'Потрібне відновлення',unavailable:'Місцеположення недоступне',
    search:'Пошук місця',needsOrigin:'Потрібна початкова точка',manual:'Вибір початкової точки',
    preview:'Попередній перегляд маршруту',guidance:'Підказки маршруту',degraded:'Погіршена точність',lost:'Місцеположення втрачено',
    progressHold:'Прогрес маршруту невідомий',arrival:'Прибуття',menu:'Меню',advanced:'Додатково',
    incident:'Повідомити про проблему',savedReports:'Збережені звіти',
    permissions:'Дозвіл на геодані',searchEmpty:'Нічого не знайдено',routeError:'Маршрут недоступний',paused:'Підказки призупинено',background:'Навігація у тлі',departure:'Поза маршрутом',recovering:'Перевірка позиції',restart:'Сеанс завершено',incidentDelete:'Видалення звіту',incidentFailure:'Звіт не збережено',
    permissionDenied:'Доступ до геоданих вимкнено',routeCalculating:'Підготовка маршруту',destinationRetained:'Пункт збережено',partialOffline:'Мапа доступна',offlineNetwork:'Підготовку призупинено',active:'Активна навігація',freeSpace:'Прив’язка до маршруту недоступна',arrivalPending:'Поблизу пункту призначення',
    permissionDeniedTitle:'Доступ до геоданих вимкнено',permissionDeniedBody:'Офлайн-мапа залишається доступною. Виберіть початкову точку для планування маршруту. Поточний прогрес чекатиме на дозвіл і надійне місцеположення.',openPermissionSettings:'Перевірити дозвіл на геодані',
    partialOfflineTitle:'Мапа доступна; навігація не готова',partialOfflineBody:'Цю перевірену мапу можна переглядати. Маршрути й пошук місць недоступні, доки всі потрібні офлайн-дані не буде перевірено.',exploreMap:'Переглянути доступну мапу',
    offlineNetworkTitle:'Для підготовки потрібне з’єднання',offlineNetworkBody:'Не вдалося завершити завантаження потрібних офлайн-даних. Уже перевірена мапа залишається доступною; навігація ще не готова. Повторіть спробу, коли з’явиться з’єднання.',retryPreparation:'Повторити підготовку',
    destinationRetainedTitle:'Пункт призначення збережено',destinationRetainedBody:'Вибраний пункт призначення залишається. Виберіть початкову точку або змініть пункт призначення, коли будете готові.',
    routeCalculatingTitle:'Готуємо пішохідний маршрут',routeCalculatingBody:'Пункт призначення залишається вибраним. Підказки неактивні, доки офлайн-маршрут не перевірено й ви не натиснете «Почати навігацію».',
    activeTitle:'Рух за маршрутом',activeBody:'Повороти й відстань показуються лише тоді, коли поточне місцеположення і прогрес маршруту підтверджені.',nextTurn:'Наступний поворот · приблизно 80 м',
    freeSpaceTitle:'Прив’язка до маршруту недоступна',freeSpaceBody:'Поточне місцеположення може залишатися надійним, навіть якщо жоден шлях маршруту не підтверджено. Прогрес і прибуття призупинено до появи придатної прив’язки.',
    arrivalPendingTitle:'Поблизу пункту призначення',arrivalPendingBody:'Маршрут ще активний. Прибуття не підтверджено, доки його не підтвердять свіжі, незалежні й достатньо певні дані про місцеположення.',
    permissionsTitle:'Як використовувати місцеположення',permissionsBody:'Дозвольте доступ до геоданих для поточних підказок. Без нього можна переглядати офлайн-мапу й вибрати початкову точку. Доступ у тлі запитується лише за потреби під час активної навігації.',allowLocation:'Перевірити доступ до геоданих',notNow:'Не зараз',
    searchEmptyTitle:'Офлайн-місць не знайдено',searchEmptyBody:'Спробуйте іншу назву. Вибраний пункт призначення не зміниться, доки ви не виберете новий результат.',trySearch:'Змінити запит',
    routeErrorTitle:'Не вдалося підготувати маршрут',routeErrorBody:'Пункт призначення збережено. Перевірте готовність офлайн-даних або виберіть іншу початкову точку; навігація не почалася.',tryRoute:'Спробувати ще раз',
    pausedTitle:'Підказки призупинено',pausedBody:'Маршрут і пункт призначення залишаються. Прогрес і прибуття не оновлюватимуться, доки ви не відновите підказки й не з’явиться свіжа надійна позиція.',
    backgroundTitle:'Навігація триває у тлі',backgroundBody:'Підказки можуть тривати лише під час явно активного навігаційного сервісу. Поверніться до мапи, щоб перевірити поточну надійність.',openNavigation:'Відкрити навігацію',
    departureTitle:'Можливо, ви поза маршрутом',departureBody:'Пункт призначення й запланований маршрут залишаються. Прогрес призупинено, доки прив’язку до маршруту не буде надійно підтверджено; автоматична прив’язка не стверджується.',
    recoveringTitle:'Перевіряємо вашу позицію',recoveringBody:'Нові дані про місцеположення проходять перевірку. Прогрес відновиться лише після свіжих придатних даних і підтвердженої прив’язки до маршруту.',
    restartTitle:'Попередня навігація вже не активна',restartBody:'Після нового запуску стара впевненість і прогрес маршруту не вважаються поточними. Виберіть пункт призначення або дочекайтеся нової надійної позиції.',
    incidentDeleteTitle:'Видалити цей збережений звіт?',incidentDeleteBody:'Буде видалено лише цей звіт. Скасування збереже його на пристрої.',confirmDelete:'Видалити цей звіт',
    incidentFailureTitle:'Звіт не збережено',incidentFailureBody:'Звіт не збережено. Повторіть спробу лише після перегляду поточних відомостей і підтвердження згоди.',
    menuButton:'Меню',where:'Куди прямуєте?',offlineReady:'Офлайн-навігація готова',
    positionUnavailable:'Місцеположення недоступне',positionReliable:'Місцеположення надійне',
    positionEstimated:'Орієнтовне місцеположення',positionLost:'Місцеположення втрачено',
    preparingTitle:'Готуємо офлайн-навігацію',preparingBody:'Перевіряємо мапу, маршрути та місця. Навігація стане доступною після перевірки всіх потрібних даних.',
    repairTitle:'Офлайн-навігацію треба відновити',repairBody:'Потрібних офлайн-даних немає або їх не вдалося перевірити. Навігація ще не готова.',
    repairAction:'Відновити офлайн-дані',unavailableTitle:'Мапу можна переглядати',
    unavailableBody:'Поточне місцеположення недоступне. Знайдіть пункт призначення або виберіть початкову точку під час планування маршруту.',
    searchTitle:'Знайти пункт призначення',searchHint:'Пошук офлайн-місць',searchSub:'Офлайн-місця Харкова',
    result:'Сумська, 10',resultType:'Адреса · офлайн-результат',cancel:'Скасувати',back:'Назад',
    needsOriginTitle:'Виберіть початкову точку',needsOriginBody:'Поточне місцеположення недоступне. Виберіть точку на мапі, щоб прокласти маршрут.',
    to:'До',manualNotMeasured:'Точка, яку ви вибрали на мапі, не є виміряним місцеположенням.',
    chooseStart:'Вибрати початкову точку',changeDestination:'Змінити пункт призначення',
    manualTitle:'Позначте початкову точку',manualBody:'Пересувайте мапу, доки потрібна точка не опиниться під позначкою, а потім підтвердьте вибір.',
    chosenStart:'Вибраний початок',
    confirmManual:'Підтвердити початкову точку',manualSource:'Початкову точку вибрано на мапі',
    low:'Низька впевненість',uncertainty:'Невизначеність ручного початку: ±100 м',estimatedUncertainty:'Невизначеність поточної позиції має походити з поточної придатної оцінки.',
    previewTitle:'Перегляньте пішохідний маршрут',routeDistance:'Близько 310 м',routeKind:'Офлайн-пішохідний маршрут',
    startGuidance:'Почати навігацію',previewNote:'Підказки чекатимуть на надійне поточне місцеположення. Вибрана початкова точка не є виміряною позицією.',
    guidanceTitle:'Очікуємо надійне місцеположення',guidanceBody:'Маршрут залишається видимим. Покрокові підказки чекають на надійне поточне місцеположення.',
    pause:'Призупинити підказки',resume:'Відновити підказки',stop:'Зупинити навігацію',
    degradedTitle:'Місцеположення орієнтовне',degradedBody:'Позиція менш певна. Звіряйтеся з мапою обережно; точний прогрес маршруту призупинено, якщо його не підтверджено.',
    lostTitle:'Місцеположення недоступне',lostBody:'Надійного поточного місцеположення немає. Маршрут і пункт призначення залишаються видимими, але прогрес і прибуття не стверджуються.',
    progressTitle:'Прогрес маршруту невідомий',progressBody:'Поточне місцеположення доступне, але прив’язка до маршруту непевна. Покроковий прогрес призупинено, доки його знову не буде підтверджено.',
    arrivalTitle:'Ви прибули до пункту призначення',arrivalBody:'Прибуття показується лише за свіжими, перевіреними та достатньо точними даними про місцеположення.',
    done:'Готово',navigation:'Навігація',advancedLink:'Додатково',reportProblem:'Повідомити про проблему',savedLink:'Збережені звіти про проблеми',
    advancedTitle:'Додаткові засоби',advancedBody:'Технічні відомості й навчальні засоби відокремлено від повсякденної навігації.',
    diagnostics:'Діагностика',trainer:'Тренажер',offlineDetails:'Відомості про офлайн-дані',recordings:'Записи',
    incidentTitle:'Перегляд звіту про проблему',incidentBody:'Звіт містить нещодавні категорії стану навігації та надійності, але не координати й не історію маршруту.',
    incidentEvent:'Місцеположення недоступне · прогрес призупинено',incidentConsent:'Я переглянув(ла) ці відомості й погоджуюся зберегти цей звіт на пристрої.',
    incidentSave:'Зберегти на пристрої',incidentCancel:'Не зберігати',savedTitle:'Збережені звіти про проблеми',
    savedBody:'Звіти залишаються на цьому пристрої, доки ви їх не видалите. Поширення не входить до цього MVP.',
    savedExample:'Приклад звіту · надійність навігації',delete:'Видалити звіт',
    offline:'Офлайн',position:'Позиція',manualLabel:'Ручний початок',confidence:'Впевненість',
    route:'Маршрут',selected:'Вибраний пункт',noReady:'Не готово',noProgress:'Прогрес призупинено',
    returnMap:'Повернутися до мапи',unverified:'Лише дизайн-стан — не поточні дані про позицію',
    prototypeBadge:'Дизайн-макет · історичне зображення мапи, не поточна навігація'
  }
};
const app = document.getElementById('app');
const prototypeBadge = document.getElementById('prototypeBadge');
const stateSelect = document.getElementById('state');
const languageSelect = document.getElementById('language');
const scaleSelect = document.getElementById('scale');
const themeSelect = document.getElementById('theme');
const params = new URLSearchParams(location.search);
let state = screens.includes(params.get('screen')) ? params.get('screen') : 'ready';
let language = params.get('lang') === 'uk' ? 'uk' : 'en';
let scale = ['1','1.5','2'].includes(params.get('scale')) ? params.get('scale') : '1';
let theme = params.get('theme') === 'dark' ? 'dark' : 'light';
let consent = false;
let retainedPosition = 'unavailable';
let selectedDestination = false;

const btn = (label, next, kind='secondary') => `<button class="btn ${kind}" data-next="${next}">${label}</button>`;
const actions = (...items) => `<div class="actions">${items.join('')}</div>`;
const safety = (title, body, danger=false) => `<div class="safety ${danger?'danger-note':''}" role="note"><strong>${title}</strong><p>${body}</p></div>`;
const sheet = (title, body, tall=false) => `<section class="sheet panel ${tall?'tall':''}" aria-label="${title}"><h2>${title}</h2>${body}</section>`;
const page = (title, body, backTo='menu') => `<section class="page" aria-label="${title}"><div class="page-head">${btn(t.back,backTo,'tertiary')}<h2>${title}</h2></div>${body}</section>`;
let t = copy.en;

function layout(s) {
  const to = `<div class="rowline"><span>${t.to}</span><strong>${t.result}</strong></div>`;
  if (s === 'ready') return sheet(t.offlineReady, `<p class="lede">${t.positionUnavailable}</p>${selectedDestination?to:''}${actions(btn(t.where,'search','primary'),...(selectedDestination?[btn(t.chooseStart,'needsOrigin')]:[]))}`);
  if (s === 'permissions') return sheet(t.permissionsTitle, `<p>${t.permissionsBody}</p>${actions(btn(t.allowLocation,'unavailable','primary'),btn(t.notNow,'unavailable','tertiary'))}`,true);
  if (s === 'permissionDenied') return sheet(t.permissionDeniedTitle, `<p>${t.permissionDeniedBody}</p>${actions(btn(t.openPermissionSettings,'permissions','primary'),btn(t.where,'search'),btn(t.notNow,'unavailable','tertiary'))}`,true);
  if (s === 'preparing') return sheet(t.preparingTitle, `<p>${t.preparingBody}</p><div class="bar" aria-label="Design-only preparation progress"><span></span></div><p class="caption">${t.positionUnavailable}</p>`);
  if (s === 'repair') return sheet(t.repairTitle, `<p>${t.repairBody}</p>${actions(btn(t.repairAction,'preparing','primary'))}`);
  if (s === 'partialOffline') return sheet(t.partialOfflineTitle, `<p>${t.partialOfflineBody}</p>${actions(btn(t.repairAction,'preparing','primary'))}`,true);
  if (s === 'offlineNetwork') return sheet(t.offlineNetworkTitle, `<p>${t.offlineNetworkBody}</p>${actions(btn(t.retryPreparation,'preparing','primary'),btn(t.exploreMap,'partialOffline'))}`,true);
  if (s === 'unavailable') return sheet(t.unavailableTitle, `<p>${t.unavailableBody}</p>${actions(btn(t.where,'search','primary'))}`);
  if (s === 'search') return sheet(t.searchTitle, `<label for="destination">${t.searchHint}</label><input id="destination" class="field" type="search" placeholder="${t.searchHint}" autocomplete="off"><p class="caption">${t.searchSub}</p><ul class="list"><li><button data-next="needsOrigin"><strong>${t.result}</strong><small>${t.resultType}</small></button></li></ul>${actions(btn(t.cancel,'searchCancel','tertiary'))}`,true);
  if (s === 'searchEmpty') return sheet(t.searchEmptyTitle, `<p>${t.searchEmptyBody}</p>${actions(btn(t.trySearch,'search','primary'),btn(t.cancel,'searchCancel','tertiary'))}`);
  if (s === 'destinationRetained') return sheet(t.destinationRetainedTitle, `<p>${t.destinationRetainedBody}</p>${to}${actions(btn(t.chooseStart,'needsOrigin','primary'),btn(t.changeDestination,'search'),btn(t.returnMap,'ready','tertiary'))}`,true);
  if (s === 'routeCalculating') return sheet(t.routeCalculatingTitle, `<p>${t.routeCalculatingBody}</p>${to}${safety(t.manualSource,`${t.low}. ${t.uncertainty}.`)}${actions(btn(t.cancel,'destinationRetained','tertiary'))}`,true);
  if (s === 'routeError') return sheet(t.routeErrorTitle, `<p>${t.routeErrorBody}</p>${to}${safety(t.manualSource,`${t.low}. ${t.uncertainty}.`)}${actions(btn(t.tryRoute,'routeCalculating','primary'),btn(t.chooseStart,'needsOrigin'),btn(t.changeDestination,'search'),btn(t.cancel,'destinationRetained','tertiary'))}`,true);
  if (s === 'needsOrigin') return sheet(t.needsOriginTitle, `<p>${t.needsOriginBody}</p>${to}${safety(t.positionUnavailable,t.manualNotMeasured)}${actions(btn(t.chooseStart,'manual','primary'),btn(t.changeDestination,'search'),btn(t.cancel,'destinationRetained','tertiary'))}`,true);
  if (s === 'manual') return sheet(t.manualTitle, `<p>${t.manualBody}</p>${to}${safety(t.low,t.manualNotMeasured)}${actions(btn(t.confirmManual,'routeCalculating','primary'),btn(t.cancel,'needsOrigin','tertiary'))}`,true);
  if (s === 'preview') return sheet(t.previewTitle, `<p><strong>${t.routeDistance}</strong> · ${t.routeKind}</p>${to}${safety(t.manualSource,`${t.low}. ${t.uncertainty}. ${t.previewNote}`)}${actions(btn(t.startGuidance,'guidance','primary'),btn(t.changeDestination,'search'),btn(t.back,'destinationRetained','tertiary'))}`,true);
  if (s === 'guidance') return sheet(t.guidanceTitle, `<p>${t.guidanceBody}</p>${to}${safety(t.manualSource,`${t.low}. ${t.uncertainty}.`)}${actions(btn(t.pause,'paused'),btn(t.reportProblem,'incident','tertiary'),btn(t.stop,'ready','danger'))}`,true);
  if (s === 'active') return sheet(t.activeTitle, `<p>${t.activeBody}</p>${to}<p class="lede">${t.nextTurn}</p>${safety(t.manualSource,`${t.low}. ${t.uncertainty}.`)}${actions(btn(t.pause,'paused'),btn(t.reportProblem,'incident','tertiary'),btn(t.stop,'ready','danger'))}`,true);
  if (s === 'paused') return sheet(t.pausedTitle, `<p>${t.pausedBody}</p>${to}${safety(t.manualSource,`${t.low}. ${t.uncertainty}. ${t.noProgress}`)}${actions(btn(t.resume,'recovering','primary'),btn(t.stop,'ready','danger'))}`,true);
  if (s === 'background') return sheet(t.backgroundTitle, `<p>${t.backgroundBody}</p>${to}${safety(t.manualSource,`${t.low}. ${t.uncertainty}.`)}${actions(btn(t.openNavigation,'recovering','primary'),btn(t.stop,'ready','danger'))}`,true);
  if (s === 'departure') return sheet(t.departureTitle, `<p>${t.departureBody}</p>${to}<p class="caption">${t.positionReliable} · ${t.noProgress}</p>${safety(t.manualSource,`${t.low}. ${t.uncertainty}.`)}${actions(btn(t.reportProblem,'incident'),btn(t.stop,'ready','danger'))}`,true);
  if (s === 'freeSpace') return sheet(t.freeSpaceTitle, `<p>${t.freeSpaceBody}</p>${to}<p class="caption">${t.positionReliable} · ${t.noProgress}</p>${safety(t.manualSource,`${t.low}. ${t.uncertainty}.`)}${actions(btn(t.reportProblem,'incident'),btn(t.stop,'ready','danger'))}`,true);
  if (s === 'recovering') return sheet(t.recoveringTitle, `<p>${t.recoveringBody}</p>${to}${safety(t.manualSource,`${t.low}. ${t.uncertainty}. ${t.noProgress}`)}${actions(btn(t.stop,'ready','danger'))}`,true);
  if (s === 'restart') return sheet(t.restartTitle, `<p>${t.restartBody}</p>${actions(btn(t.where,'search','primary'))}`);
  if (s === 'degraded') return sheet(t.degradedTitle, `<p>${t.degradedBody}</p>${to}${safety(t.positionEstimated,`${t.low}. ${t.estimatedUncertainty}`)}${safety(t.manualSource,`${t.low}. ${t.uncertainty}.`)}${actions(btn(t.reportProblem,'incident'),btn(t.stop,'ready','danger'))}`,true);
  if (s === 'lost') return sheet(t.lostTitle, `<p>${t.lostBody}</p>${to}${safety(t.positionLost,t.noProgress,true)}${safety(t.manualSource,`${t.low}. ${t.uncertainty}.`)}${actions(btn(t.reportProblem,'incident'),btn(t.stop,'ready','danger'))}`,true);
  if (s === 'progressHold') return sheet(t.progressTitle, `<p>${t.progressBody}</p>${to}<p class="caption">${t.positionReliable} · ${t.noProgress}</p>${safety(t.manualSource,`${t.low}. ${t.uncertainty}.`)}${actions(btn(t.reportProblem,'incident'),btn(t.stop,'ready','danger'))}`,true);
  if (s === 'arrivalPending') return sheet(t.arrivalPendingTitle, `<p>${t.arrivalPendingBody}</p>${to}${safety(t.manualSource,`${t.low}. ${t.uncertainty}. ${t.noProgress}`)}${actions(btn(t.stop,'ready','danger'))}`,true);
  if (s === 'arrival') return sheet(t.arrivalTitle, `<p>${t.arrivalBody}</p>${to}<p class="caption">${t.positionReliable}</p>${safety(t.manualSource,`${t.low}. ${t.uncertainty}.`)}${actions(btn(t.done,'ready','primary'))}`,true);
  if (s === 'menu') return page(t.menu, `<section><h3>${t.navigation}</h3><ul class="list"><li><button data-next="search">${t.where}</button></li><li><button data-next="incident">${t.reportProblem}</button></li><li><button data-next="savedReports">${t.savedLink}</button></li></ul></section><section><h3>${t.advancedLink}</h3><ul class="list"><li><button data-next="advanced">${t.advancedTitle}</button></li></ul></section>${actions(btn(t.returnMap,'ready','tertiary'))}`,'ready');
  if (s === 'advanced') return page(t.advancedTitle, `<p>${t.advancedBody}</p><ul class="list"><li><button>${t.diagnostics}</button></li><li><button>${t.trainer}</button></li><li><button>${t.offlineDetails}</button></li><li><button>${t.recordings}</button></li></ul><p class="screen-note">${t.unverified}</p>`);
  if (s === 'incident') return page(t.incidentTitle, `<p>${t.incidentBody}</p><div class="safety"><strong>${t.incidentEvent}</strong><p>${t.low}. ${t.uncertainty}.</p></div><label class="checkbox"><input id="consent" type="checkbox" ${consent?'checked':''}><span>${t.incidentConsent}</span></label>${actions(`<button id="save" class="btn primary" ${consent?'':'disabled'} data-next="savedReports">${t.incidentSave}</button>`,btn(t.incidentCancel,'guidance','tertiary'))}`,'guidance');
  if (s === 'savedReports') return page(t.savedTitle, `<p>${t.savedBody}</p><ul class="list"><li><button>${t.savedExample}</button></li></ul>${actions(btn(t.delete,'incidentDelete','danger'),btn(t.returnMap,'ready','tertiary'))}`);
  if (s === 'incidentDelete') return page(t.incidentDeleteTitle, `<p>${t.incidentDeleteBody}</p>${actions(btn(t.confirmDelete,'savedReports','danger'),btn(t.cancel,'savedReports','tertiary'))}`,'savedReports');
  if (s === 'incidentFailure') return page(t.incidentFailureTitle, `<p>${t.incidentFailureBody}</p>${actions(btn(t.cancel,'guidance','tertiary'))}`,'guidance');
  return '';
}

const offlineNotReadyStates = ['preparing','repair','partialOffline','offlineNetwork'];
const reliablePositionStates = ['active','progressHold','freeSpace','departure','arrivalPending','arrival'];
const routeStates = ['routeCalculating','routeError','preview','guidance','active','progressHold','freeSpace','arrivalPending','arrival','paused','background','departure','recovering','lost','degraded'];
const mapScrimStates = ['search','searchEmpty','needsOrigin','manual','destinationRetained',...routeStates];
function positionClass(s) {
  if (['paused','background'].includes(s)) return retainedPosition;
  if (reliablePositionStates.includes(s)) return 'reliable';
  if (s === 'degraded') return 'estimated';
  if (s === 'lost') return 'lost';
  return 'unavailable';
}
function resolveUserTransition(from, requested) {
  if (from==='search' && requested==='needsOrigin') selectedDestination = true;
  if (requested==='searchCancel') return selectedDestination ? 'destinationRetained' : 'ready';
  return requested;
}

function render() {
  t = copy[language];
  prototypeBadge.textContent = t.prototypeBadge;
  document.documentElement.lang = language;
  document.documentElement.dataset.theme = theme;
  app.dataset.theme = theme;
  app.style.setProperty('--scale',scale);
  stateSelect.innerHTML = screens.map(s=>`<option value="${s}">${t[s]}</option>`).join('');
  stateSelect.value = state;
  languageSelect.value = language; scaleSelect.value = scale; themeSelect.value = theme;
  const full = ['menu','advanced','incident','savedReports','incidentDelete','incidentFailure'].includes(state);
  const offlineReady = !offlineNotReadyStates.includes(state);
  const position = positionClass(state);
  const positionText = { reliable:t.positionReliable, estimated:t.positionEstimated, lost:t.positionLost, unavailable:t.positionUnavailable }[position];
  const searchAction = offlineReady ? btn(t.where,'search','search-action') : `<button class="btn search-action" disabled aria-disabled="true">${t.where}</button>`;
  const top = full ? '' : `<header class="top"><div class="top-actions">${btn(t.menuButton,'menu','top-btn')}${searchAction}</div></header><div class="top-status"><span class="chip ${offlineReady?'ready':'caution'}">${offlineReady?t.offlineReady:t.noReady}</span><span class="chip ${position==='lost'?'danger':position==='estimated'?'caution':position==='reliable'?'ready':'neutral'}">${positionText}</span></div>`;
  app.innerHTML = `<div class="map ${routeStates.includes(state)?'route-map':''}" aria-hidden="true"></div>${mapScrimStates.includes(state)?'<div class="map-scrim" aria-hidden="true"></div>':''}${state==='manual'?`<div class="map-target" aria-label="${t.chosenStart}">${t.chosenStart}</div>`:''}${top}${layout(state)}`;
  app.querySelectorAll('[data-next]').forEach(el=>el.addEventListener('click',()=>{
    if (['paused','background'].includes(el.dataset.next)) retainedPosition = positionClass(state);
    state=resolveUserTransition(state,el.dataset.next);
    consent=false;render();
  }));
  const check=app.querySelector('#consent');
  if (check) check.addEventListener('change',()=>{consent=check.checked;app.querySelector('#save').disabled=!consent;});
  const url = new URL(location.href); url.searchParams.set('screen',state);url.searchParams.set('lang',language);url.searchParams.set('scale',scale);url.searchParams.set('theme',theme);
  history.replaceState(null,'',url);
}
stateSelect.addEventListener('change',e=>{state=e.target.value;retainedPosition='unavailable';consent=false;render();});
languageSelect.addEventListener('change',e=>{language=e.target.value;render();});
scaleSelect.addEventListener('change',e=>{scale=e.target.value;render();});
themeSelect.addEventListener('change',e=>{theme=e.target.value;render();});
render();
