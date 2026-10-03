-- Demo data: Riverside District (the demo), Old Town (where Stair Buddies started), a campus and a co-op.
-- Embeddings stay NULL here: at startup the app fills them with the configured embedding model
-- and asks the matcher for a suggestion on every TRIAGED case (see BackgroundJobs).
-- Demo logins (X-Demo-User header): maria, ewa (moderator), kasia (Good Neighbours), jan (welfare office),
-- piotr (admin), anna / tomasz / ola (volunteers), zofia (Old Town moderator).

CREATE FUNCTION seed_labels(en text, pl text, uk text DEFAULT NULL) RETURNS jsonb
    LANGUAGE sql IMMUTABLE AS $$ SELECT jsonb_strip_nulls(jsonb_build_object('en', en, 'pl', pl, 'uk', uk)) $$;

-- A person's words in the language they wrote them in (report bodies)
CREATE FUNCTION seed_text(src text, en text, pl text, uk text DEFAULT NULL) RETURNS jsonb
    LANGUAGE sql IMMUTABLE AS $$ SELECT jsonb_build_object('source', src, 'values', seed_labels(en, pl, uk)) $$;

-- Curated or AI-written text, written in each language directly ("source": "*", see LocalizedText)
CREATE FUNCTION seed_i18n(en text, pl text, uk text DEFAULT NULL) RETURNS jsonb
    LANGUAGE sql IMMUTABLE AS $$ SELECT jsonb_build_object('source', '*', 'values', seed_labels(en, pl, uk)) $$;

CREATE FUNCTION seed_step(pos int, title_en text, title_pl text, desc_en text, desc_pl text, roles text[]) RETURNS jsonb
    LANGUAGE sql IMMUTABLE AS $$
    SELECT jsonb_build_object('position', pos,
                              'title', seed_i18n(title_en, title_pl),
                              'description', seed_i18n(desc_en, desc_pl),
                              'roles', to_jsonb(roles),
                              'change', NULL)
$$;

-- ---------------------------------------------------------------------------
-- Communities and their configuration
-- ---------------------------------------------------------------------------
INSERT INTO community (id, slug, kind, name, default_locale, locales, config) VALUES
('10000000-0000-4000-8000-000000000001', 'riverside', 'CITY_DISTRICT',
 seed_i18n('Riverside District', 'Dzielnica Nadrzecze', 'Район Річковий'), 'pl', '{pl,en,uk}',
 '{
   "categories": [
     {"code": "seniors",  "labels": {"en": "Seniors", "pl": "Seniorzy", "uk": "Літні люди"}},
     {"code": "safety",   "labels": {"en": "Safety", "pl": "Bezpieczeństwo", "uk": "Безпека"}},
     {"code": "streets",  "labels": {"en": "Streets and transport", "pl": "Ulice i transport", "uk": "Вулиці та транспорт"}},
     {"code": "green",    "labels": {"en": "Green spaces", "pl": "Zieleń", "uk": "Зелені зони"}},
     {"code": "youth",    "labels": {"en": "Youth", "pl": "Młodzież", "uk": "Молодь"}},
     {"code": "housing",  "labels": {"en": "Housing", "pl": "Mieszkania", "uk": "Житло"}},
     {"code": "culture",  "labels": {"en": "Culture and events", "pl": "Kultura i wydarzenia", "uk": "Культура та події"}},
     {"code": "ideas",    "labels": {"en": "Ideas for the district", "pl": "Pomysły dla dzielnicy", "uk": "Ідеї для району"}}
   ],
   "workflow": [
     {"status": "NEW",         "labels": {"en": "New", "pl": "Nowe", "uk": "Нове"}},
     {"status": "TRIAGED",     "labels": {"en": "Triaged", "pl": "Sprawdzone", "uk": "Перевірено"}},
     {"status": "SUGGESTED",   "labels": {"en": "Suggested", "pl": "Zaproponowane", "uk": "Запропоновано"}},
     {"status": "MATCHED",     "labels": {"en": "Matched", "pl": "Dopasowane", "uk": "Підібрано"}},
     {"status": "IN_PROGRESS", "labels": {"en": "In progress", "pl": "W toku", "uk": "У роботі"}},
     {"status": "RESOLVED",    "labels": {"en": "Resolved", "pl": "Rozwiązane", "uk": "Вирішено"}},
     {"status": "CONFIRMED",   "labels": {"en": "Confirmed", "pl": "Potwierdzone", "uk": "Підтверджено"}},
     {"status": "CLOSED",      "labels": {"en": "Closed", "pl": "Zamknięte", "uk": "Закрито"}}
   ],
   "roles": [
     {"role": "RESIDENT",  "who": {"en": "Anyone who lives or works in Riverside", "pl": "Każdy, kto mieszka lub pracuje w dzielnicy"}},
     {"role": "MODERATOR", "who": {"en": "Community workers and district council members", "pl": "Animatorzy społeczni i radni dzielnicy"}},
     {"role": "DOER",      "who": {"en": "City offices, NGOs and volunteer groups", "pl": "Urzędy, organizacje pozarządowe i grupy wolontariuszy"}},
     {"role": "ADMIN",     "who": {"en": "The district office", "pl": "Urząd dzielnicy"}}
   ],
   "rules": {"mergeRadiusMeters": 600, "publicLocationRoundingMeters": 100, "autoCloseDays": 14, "publicMap": true}
 }'::jsonb),

('10000000-0000-4000-8000-000000000002', 'old-town', 'CITY_DISTRICT',
 seed_i18n('Old Town', 'Stare Miasto'), 'pl', '{pl,en}',
 '{
   "categories": [
     {"code": "seniors", "labels": {"en": "Seniors", "pl": "Seniorzy"}},
     {"code": "safety",  "labels": {"en": "Safety", "pl": "Bezpieczeństwo"}},
     {"code": "green",   "labels": {"en": "Green spaces", "pl": "Zieleń"}},
     {"code": "youth",   "labels": {"en": "Youth", "pl": "Młodzież"}},
     {"code": "ideas",   "labels": {"en": "Ideas for the district", "pl": "Pomysły dla dzielnicy"}}
   ],
   "workflow": [
     {"status": "NEW",         "labels": {"en": "New", "pl": "Nowe"}},
     {"status": "TRIAGED",     "labels": {"en": "Triaged", "pl": "Sprawdzone"}},
     {"status": "MATCHED",     "labels": {"en": "Matched", "pl": "Dopasowane"}},
     {"status": "IN_PROGRESS", "labels": {"en": "In progress", "pl": "W toku"}},
     {"status": "RESOLVED",    "labels": {"en": "Resolved", "pl": "Rozwiązane"}},
     {"status": "CLOSED",      "labels": {"en": "Closed", "pl": "Zamknięte"}}
   ],
   "rules": {"mergeRadiusMeters": 600, "publicLocationRoundingMeters": 100, "autoCloseDays": 14, "publicMap": true}
 }'::jsonb),

('10000000-0000-4000-8000-000000000003', 'north-campus', 'CAMPUS',
 seed_i18n('North Campus', 'Kampus Północny'), 'pl', '{pl,en}',
 '{
   "categories": [
     {"code": "study",           "labels": {"en": "Study spaces", "pl": "Miejsca do nauki"}},
     {"code": "mental-health",   "labels": {"en": "Mental health", "pl": "Zdrowie psychiczne"}},
     {"code": "accessibility",   "labels": {"en": "Accessibility", "pl": "Dostępność"}},
     {"code": "student-housing", "labels": {"en": "Student housing", "pl": "Akademiki"}},
     {"code": "night-safety",    "labels": {"en": "Safety at night", "pl": "Bezpieczeństwo nocą"}},
     {"code": "clubs",           "labels": {"en": "Clubs and events", "pl": "Koła i wydarzenia"}},
     {"code": "ideas",           "labels": {"en": "Ideas for campus", "pl": "Pomysły dla kampusu"}}
   ],
   "workflow": [
     {"status": "NEW",         "labels": {"en": "New", "pl": "Nowe"}},
     {"status": "TRIAGED",     "labels": {"en": "Triaged", "pl": "Sprawdzone"}},
     {"status": "MATCHED",     "labels": {"en": "Matched", "pl": "Dopasowane"}},
     {"status": "IN_PROGRESS", "labels": {"en": "In progress", "pl": "W toku"}},
     {"status": "RESOLVED",    "labels": {"en": "Resolved", "pl": "Rozwiązane"}},
     {"status": "CLOSED",      "labels": {"en": "Closed", "pl": "Zamknięte"}}
   ],
   "roles": [
     {"role": "RESIDENT",  "who": {"en": "Students and staff", "pl": "Studenci i pracownicy"}},
     {"role": "MODERATOR", "who": {"en": "Student union and the dean’s office", "pl": "Samorząd studencki i dziekanat"}},
     {"role": "DOER",      "who": {"en": "Campus services, societies and volunteers", "pl": "Służby kampusu, koła i wolontariusze"}},
     {"role": "ADMIN",     "who": {"en": "University IT", "pl": "Dział IT uczelni"}}
   ],
   "rules": {"mergeRadiusMeters": 150, "publicLocationRoundingMeters": 50, "autoCloseDays": 14, "publicMap": true}
 }'::jsonb),

('10000000-0000-4000-8000-000000000004', 'oak-street-coop', 'HOUSING_COOP',
 seed_i18n('Oak Street Co-op', 'Spółdzielnia Dębowa'), 'pl', '{pl}',
 '{
   "categories": [
     {"code": "repairs",       "labels": {"en": "Repairs", "pl": "Naprawy"}},
     {"code": "shared-spaces", "labels": {"en": "Shared spaces", "pl": "Części wspólne"}},
     {"code": "neighbours",    "labels": {"en": "Neighbours", "pl": "Sąsiedzi"}},
     {"code": "safety",        "labels": {"en": "Safety", "pl": "Bezpieczeństwo"}},
     {"code": "courtyard",     "labels": {"en": "Courtyard", "pl": "Podwórko"}},
     {"code": "ideas",         "labels": {"en": "Ideas for the building", "pl": "Pomysły dla budynku"}}
   ],
   "workflow": [
     {"status": "NEW",       "labels": {"en": "New", "pl": "Nowe"}},
     {"status": "TRIAGED",   "labels": {"en": "Discussed", "pl": "Omówione"}},
     {"status": "MATCHED",   "labels": {"en": "Assigned", "pl": "Przydzielone"}},
     {"status": "RESOLVED",  "labels": {"en": "Done", "pl": "Zrobione"}},
     {"status": "CONFIRMED", "labels": {"en": "Confirmed", "pl": "Potwierdzone"}}
   ],
   "roles": [
     {"role": "RESIDENT",  "who": {"en": "Co-op members and tenants", "pl": "Członkowie spółdzielni i najemcy"}},
     {"role": "MODERATOR", "who": {"en": "The members’ council", "pl": "Rada członków"}},
     {"role": "DOER",      "who": {"en": "Building manager, contractors, volunteer members", "pl": "Zarządca, wykonawcy, członkowie-wolontariusze"}},
     {"role": "ADMIN",     "who": {"en": "The co-op office", "pl": "Biuro spółdzielni"}}
   ],
   "rules": {"mergeRadiusMeters": 50, "publicLocationRoundingMeters": 0, "autoCloseDays": 14, "publicMap": false}
 }'::jsonb);

-- ---------------------------------------------------------------------------
-- People
-- ---------------------------------------------------------------------------
INSERT INTO app_user (id, auth_subject, display_name, locale) VALUES
('20000000-0000-4000-8000-000000000001', 'maria',  'Maria',     'pl'),
('20000000-0000-4000-8000-000000000002', 'ewa',    'Ewa N.',    'en'),
('20000000-0000-4000-8000-000000000003', 'kasia',  'Kasia L.',  'pl'),
('20000000-0000-4000-8000-000000000004', 'jan',    'Jan P.',    'pl'),
('20000000-0000-4000-8000-000000000005', 'piotr',  'Piotr K.',  'en'),
('20000000-0000-4000-8000-000000000006', 'anna',   'Anna K.',   'pl'),
('20000000-0000-4000-8000-000000000007', 'tomasz', 'Tomasz W.', 'pl'),
('20000000-0000-4000-8000-000000000008', 'ola',    'Ola M.',    'uk'),
('20000000-0000-4000-8000-000000000009', 'zofia',  'Zofia R.',  'pl');

-- 17 Riverside neighbours who already reported the walk-up problem, and 5 Old Town residents.
INSERT INTO app_user (id, auth_subject, display_name, locale)
SELECT ('20000000-0000-4000-8000-0000000001' || lpad(n::text, 2, '0'))::uuid,
       'neighbour-' || lpad(n::text, 2, '0'), 'Neighbour ' || n, CASE WHEN n % 5 = 3 THEN 'uk' ELSE 'pl' END
FROM generate_series(1, 17) AS n;

INSERT INTO app_user (id, auth_subject, display_name, locale)
SELECT ('20000000-0000-4000-8000-0000000002' || lpad(n::text, 2, '0'))::uuid,
       'oldtown-' || lpad(n::text, 2, '0'), 'Old Town resident ' || n, 'pl'
FROM generate_series(1, 5) AS n;

-- ---------------------------------------------------------------------------
-- Doers in Riverside
-- ---------------------------------------------------------------------------
INSERT INTO actor (id, community_id, kind, name, description, capabilities, service_lat, service_lng, service_radius_m, user_id, contact_email) VALUES
('30000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'NGO', 'Good Neighbours Foundation',
 seed_i18n('Recruits, vets and coordinates neighbourhood volunteers who help older residents with shopping, walks and visits.',
                 'Rekrutuje, weryfikuje i koordynuje wolontariuszy, którzy pomagają starszym sąsiadom w zakupach, spacerach i wizytach.'),
 '{seniors,volunteers,isolation}', 52.2489, 21.0429, 4000, NULL, 'hello@good-neighbours.example'),
('30000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'INSTITUTION', 'Riverside Social Welfare Office',
 seed_i18n('Social workers who know which residents need support. Lends stair-climbers with a trained operator.',
                 'Pracownicy socjalni, którzy wiedzą, kto potrzebuje wsparcia. Wypożyczają schodołazy z przeszkolonym operatorem.'),
 '{seniors,housing,care,equipment}', 52.2489, 21.0429, 6000, NULL, 'welfare@riverside.example'),
('30000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', 'INSTITUTION', 'Elm Street Housing Co-op',
 seed_i18n('Manages the 1960s blocks around Elm Street, including stairwells and entrances.',
                 'Zarządza blokami z lat 60. przy ulicy Wiązowej, w tym klatkami schodowymi i wejściami.'),
 '{housing,repairs,accessibility}', 52.2496, 21.0410, 800, NULL, 'office@elm-coop.example'),
('30000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000001', 'INSTITUTION', 'City Lighting Department',
 seed_i18n('Repairs streetlights and the lamps on paths and roads.',
                 'Naprawia latarnie i oświetlenie ścieżek oraz ulic.'),
 '{safety,streets}', 52.2489, 21.0429, 10000, NULL, 'lighting@city.example'),
('30000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000001', 'INSTITUTION', 'Parks and Green Spaces Department',
 seed_i18n('Looks after parks, trees, bins and playgrounds.',
                 'Dba o parki, drzewa, kosze na śmieci i place zabaw.'),
 '{green}', 52.2489, 21.0429, 10000, NULL, 'parks@city.example'),
('30000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000001', 'VOLUNTEER_GROUP', 'Riverside Makers',
 seed_i18n('Neighbours who repair things and run the monthly repair café.',
                 'Sąsiedzi, którzy naprawiają rzeczy i prowadzą comiesięczną kawiarenkę naprawczą.'),
 '{ideas,culture,repairs}', 52.2483, 21.0388, 3000, NULL, 'makers@riverside.example'),
('30000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000001', 'INSTITUTION', 'Linden School',
 seed_i18n('Primary school that opens its gym and library to local groups.',
                 'Szkoła podstawowa, która udostępnia salę gimnastyczną i bibliotekę lokalnym grupom.'),
 '{youth}', 52.2500, 21.0440, 2000, NULL, 'office@linden-school.example'),
('30000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000001', 'VOLUNTEER', 'Anna K.',
 seed_i18n('Lives on Elm Street. Offers weekly shopping and walks.',
                 'Mieszka przy ulicy Wiązowej. Oferuje cotygodniowe zakupy i spacery.'),
 '{seniors,shopping,walks}', 52.2497, 21.0415, 500, '20000000-0000-4000-8000-000000000006', NULL),
('30000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000001', 'VOLUNTEER', 'Tomasz W.',
 seed_i18n('Has a car. Can drive neighbours to the clinic and carry shopping upstairs.',
                 'Ma samochód. Może zawieźć sąsiadów do przychodni i wnieść zakupy na górę.'),
 '{seniors,shopping,driving}', 52.2492, 21.0420, 1000, '20000000-0000-4000-8000-000000000007', NULL),
('30000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000001', 'VOLUNTEER', 'Ola M.',
 seed_i18n('Offers walks and company. Speaks Ukrainian and Polish.',
                 'Oferuje spacery i towarzystwo. Mówi po ukraińsku i po polsku.',
                 'Пропоную прогулянки та спілкування. Розмовляю українською та польською.'),
 '{seniors,walks,youth}', 52.2499, 21.0405, 800, '20000000-0000-4000-8000-000000000008', NULL);

-- ---------------------------------------------------------------------------
-- Memberships
-- ---------------------------------------------------------------------------
INSERT INTO membership (id, user_id, community_id, role, actor_id) VALUES
(gen_random_uuid(), '20000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'RESIDENT', NULL),
(gen_random_uuid(), '20000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'MODERATOR', NULL),
(gen_random_uuid(), '20000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', 'DOER', '30000000-0000-4000-8000-000000000001'),
(gen_random_uuid(), '20000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000001', 'DOER', '30000000-0000-4000-8000-000000000002'),
(gen_random_uuid(), '20000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000001', 'ADMIN', NULL),
(gen_random_uuid(), '20000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000002', 'ADMIN', NULL),
(gen_random_uuid(), '20000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000003', 'ADMIN', NULL),
(gen_random_uuid(), '20000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000004', 'ADMIN', NULL),
(gen_random_uuid(), '20000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000001', 'RESIDENT', NULL),
(gen_random_uuid(), '20000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000001', 'DOER', '30000000-0000-4000-8000-000000000008'),
(gen_random_uuid(), '20000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000001', 'RESIDENT', NULL),
(gen_random_uuid(), '20000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000001', 'DOER', '30000000-0000-4000-8000-000000000009'),
(gen_random_uuid(), '20000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000001', 'RESIDENT', NULL),
(gen_random_uuid(), '20000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000001', 'DOER', '30000000-0000-4000-8000-000000000010'),
(gen_random_uuid(), '20000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000002', 'MODERATOR', NULL);

INSERT INTO membership (id, user_id, community_id, role)
SELECT gen_random_uuid(), ('20000000-0000-4000-8000-0000000001' || lpad(n::text, 2, '0'))::uuid,
       '10000000-0000-4000-8000-000000000001', 'RESIDENT'
FROM generate_series(1, 17) AS n;

INSERT INTO membership (id, user_id, community_id, role)
SELECT gen_random_uuid(), ('20000000-0000-4000-8000-0000000002' || lpad(n::text, 2, '0'))::uuid,
       '10000000-0000-4000-8000-000000000002', 'RESIDENT'
FROM generate_series(1, 5) AS n;

-- ---------------------------------------------------------------------------
-- Initiatives on the Riverside map
-- ---------------------------------------------------------------------------
INSERT INTO initiative (id, community_id, actor_id, title, description, schedule, category_codes, lat, lng) VALUES
('60000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000006',
 seed_i18n('Repair café at the library', 'Kawiarenka naprawcza w bibliotece'),
 seed_i18n('Bring something broken and volunteers help you fix it.', 'Przynieś coś zepsutego, a wolontariusze pomogą to naprawić.'),
 seed_i18n('Saturdays, 10:00–14:00', 'Soboty, 10:00–14:00'), '{ideas,culture}', 52.2483, 21.0388),
('60000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000007',
 seed_i18n('Homework club at Linden School', 'Pomoc w lekcjach w Szkole Lipowej'),
 seed_i18n('Older pupils and volunteers help with homework after classes.', 'Starsi uczniowie i wolontariusze pomagają w lekcjach po zajęciach.'),
 seed_i18n('Mondays and Wednesdays, 16:00–18:00', 'Poniedziałki i środy, 16:00–18:00'), '{youth}', 52.2500, 21.0440),
('60000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000006',
 seed_i18n('Community garden by the river', 'Ogród społeczny nad rzeką'),
 seed_i18n('Shared beds anyone can tend; tools are in the shed.', 'Wspólne grządki, którymi może zająć się każdy; narzędzia są w szopie.'),
 seed_i18n('Spring to autumn, weekends', 'Od wiosny do jesieni, w weekendy'), '{green}', 52.2466, 21.0476);

-- ---------------------------------------------------------------------------
-- Playbooks: proven solutions shared between communities
-- ---------------------------------------------------------------------------
INSERT INTO playbook (id, origin_community_id, slug, title, problem, category_codes, status, shared) VALUES
('40000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002', 'stair-buddies',
 seed_i18n('Stair Buddies: neighbour helpers for walk-up blocks', 'Sąsiedzka pomoc w blokach bez windy'),
 seed_i18n('Older residents on upper floors of buildings without a lift stop going out, and help nearby never reaches them.',
                 'Starsi mieszkańcy wyższych pięter w budynkach bez windy przestają wychodzić z domu, a pomoc z sąsiedztwa do nich nie dociera.'),
 '{seniors}', 'PUBLISHED', true),
('40000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'dark-path-fix',
 seed_i18n('Fix a dark path fast', 'Szybka naprawa ciemnej ścieżki'),
 seed_i18n('A broken streetlight makes a path unsafe and people avoid it after dusk.',
                 'Zepsuta latarnia sprawia, że ścieżka jest niebezpieczna i po zmroku ludzie jej unikają.'),
 '{safety,streets}', 'PUBLISHED', true),
('40000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', 'park-bins',
 seed_i18n('More bins and a weekend pickup', 'Więcej koszy i odbiór w weekend'),
 seed_i18n('Bins in busy parks overflow at weekends and litter spreads to playgrounds.',
                 'Kosze w popularnych parkach przepełniają się w weekendy, a śmieci trafiają na place zabaw.'),
 '{green}', 'PUBLISHED', true),
('40000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000002', 'youth-evening-club',
 seed_i18n('Evening club in a school gym', 'Wieczorny klub w szkolnej sali'),
 seed_i18n('Teenagers have nowhere to go in the evening and gather at bus stops.',
                 'Nastolatki nie mają dokąd pójść wieczorem i spotykają się na przystankach.'),
 '{youth}', 'PUBLISHED', true),
('40000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000002', 'tool-library',
 seed_i18n('Tool library on a shelf', 'Wypożyczalnia narzędzi na półce'),
 seed_i18n('Many households need a drill or a ladder only twice a year.',
                 'Wiele domów potrzebuje wiertarki czy drabiny tylko dwa razy w roku.'),
 '{ideas}', 'PUBLISHED', true),
('40000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000001', 'homework-help',
 seed_i18n('Homework help with retired teachers', 'Pomoc w lekcjach z emerytowanymi nauczycielami'),
 seed_i18n('Children without help at home fall behind at school.',
                 'Dzieci bez wsparcia w domu mają zaległości w szkole.'),
 '{youth}', 'PUBLISHED', true),
('40000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000002', 'check-in-calls',
 seed_i18n('Weekly check-in calls for isolated seniors', 'Cotygodniowe telefony do samotnych seniorów'),
 seed_i18n('Older people living alone can go days without talking to anyone.',
                 'Starsze osoby mieszkające samotnie potrafią przez wiele dni z nikim nie rozmawiać.'),
 '{seniors}', 'PUBLISHED', true),
('40000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000001', 'repair-cafe',
 seed_i18n('Monthly repair café', 'Comiesięczna kawiarenka naprawcza'),
 seed_i18n('Broken things get thrown away because nobody nearby can fix them.',
                 'Zepsute rzeczy lądują w koszu, bo nikt w okolicy nie potrafi ich naprawić.'),
 '{ideas,culture}', 'PUBLISHED', true);

INSERT INTO playbook_version (id, playbook_id, number, steps, effort, change_notes, drafted_by, status, published_by, published_at) VALUES
('41000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001', 1,
 jsonb_build_array(
   seed_step(1, 'Find who needs help', 'Znajdź osoby potrzebujące pomocy',
             'The social welfare office lists residents over 70 on upper floors; reports from neighbours fill the gaps.',
             'Ośrodek pomocy społecznej wskazuje osoby powyżej 70. roku życia z wyższych pięter; zgłoszenia sąsiadów uzupełniają listę.',
             '{INSTITUTION}'),
   seed_step(2, 'Recruit helpers in the same block', 'Zbierz pomocników z tego samego bloku',
             'Post a call in the stairwell and in the app. Neighbours sign up for one task a week.',
             'Wywieś ogłoszenie na klatce i w aplikacji. Sąsiedzi zapisują się na jedno zadanie w tygodniu.',
             '{NGO,VOLUNTEER}'),
   seed_step(3, 'Brief the volunteers', 'Przeszkol wolontariuszy',
             'One short meeting: safety rules, a contact person and how to flag a problem.',
             'Jedno krótkie spotkanie: zasady bezpieczeństwa, osoba kontaktowa i sposób zgłaszania problemów.',
             '{NGO}'),
   seed_step(4, 'Keep a weekly rhythm', 'Utrzymaj stały rytm',
             'Shopping on a fixed day, one outing a month and a short check-in call.',
             'Zakupy w stały dzień, jedno wyjście w miesiącu i krótki telefon kontrolny.',
             '{VOLUNTEER}')),
 jsonb_build_object('coordinatorTime', seed_labels('About 2 hours a week', 'Około 2 godzin tygodniowo'),
                    'budget', seed_labels('None', 'Brak'),
                    'firstHelp', seed_labels('Within 2 weeks', 'W ciągu 2 tygodni')),
 seed_i18n('First version, written after the Old Town pilot.', 'Pierwsza wersja, spisana po pilotażu na Starym Mieście.'),
 'PERSON', 'PUBLISHED', '20000000-0000-4000-8000-000000000009', '2025-06-01T10:00:00Z'),

('41000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000002', 1,
 jsonb_build_array(
   seed_step(1, 'Collect photos and the lamp number', 'Zbierz zdjęcia i numer latarni',
             'Residents add photos with the pole number; the moderator sends one report to the lighting department.',
             'Mieszkańcy dodają zdjęcia z numerem słupa; moderator wysyła jedno zgłoszenie do działu oświetlenia.',
             '{VOLUNTEER,INSTITUTION}'),
   seed_step(2, 'Get a repair date', 'Ustal termin naprawy',
             'The lighting department confirms a date and residents see it on the case.',
             'Dział oświetlenia potwierdza termin, a mieszkańcy widzą go w sprawie.',
             '{INSTITUTION}'),
   seed_step(3, 'Check after dark', 'Sprawdź po zmroku',
             'A resident checks the lamp after the repair and confirms in the app.',
             'Mieszkaniec sprawdza latarnię po naprawie i potwierdza w aplikacji.',
             '{VOLUNTEER}')),
 jsonb_build_object('coordinatorTime', seed_labels('About 1 hour in total', 'Około 1 godziny łącznie'),
                    'budget', seed_labels('City maintenance budget', 'Budżet miejskich napraw'),
                    'firstHelp', seed_labels('Within 1 week', 'W ciągu tygodnia')),
 NULL, 'PERSON', 'PUBLISHED', '20000000-0000-4000-8000-000000000002', '2025-11-12T09:00:00Z'),

('41000000-0000-4000-8000-000000000003', '40000000-0000-4000-8000-000000000003', 1,
 jsonb_build_array(
   seed_step(1, 'Map the overflowing bins', 'Zaznacz przepełnione kosze',
             'Residents mark the bins on the map and add photos.',
             'Mieszkańcy zaznaczają kosze na mapie i dodają zdjęcia.',
             '{VOLUNTEER}'),
   seed_step(2, 'Ask for a weekend pickup', 'Poproś o odbiór w weekend',
             'The parks department adds a Sunday pickup at the busiest bins.',
             'Zarząd zieleni dodaje niedzielny odbiór przy najbardziej obleganych koszach.',
             '{INSTITUTION}'),
   seed_step(3, 'Hold a clean-up morning', 'Zorganizuj poranne sprzątanie',
             'Neighbours clean the playground area once and invite families.',
             'Sąsiedzi raz sprzątają okolice placu zabaw i zapraszają rodziny.',
             '{VOLUNTEER_GROUP}')),
 jsonb_build_object('coordinatorTime', seed_labels('About 2 hours', 'Około 2 godzin'),
                    'budget', seed_labels('Bins from the parks budget', 'Kosze z budżetu zieleni'),
                    'firstHelp', seed_labels('Within 2 weeks', 'W ciągu 2 tygodni')),
 NULL, 'PERSON', 'PUBLISHED', '20000000-0000-4000-8000-000000000002', '2025-08-20T09:00:00Z'),

('41000000-0000-4000-8000-000000000004', '40000000-0000-4000-8000-000000000004', 1,
 jsonb_build_array(
   seed_step(1, 'Find a free room', 'Znajdź wolną salę',
             'A school opens its gym two evenings a week.',
             'Szkoła udostępnia salę gimnastyczną dwa wieczory w tygodniu.',
             '{INSTITUTION}'),
   seed_step(2, 'Find two adults to host', 'Znajdź dwie osoby dorosłe do opieki',
             'A youth worker and a volunteer run each evening.',
             'Każdy wieczór prowadzi pracownik młodzieżowy i wolontariusz.',
             '{NGO,VOLUNTEER}'),
   seed_step(3, 'Let teenagers choose', 'Pozwól młodzieży wybrać',
             'Teenagers vote on the activities every month.',
             'Młodzież co miesiąc głosuje nad zajęciami.',
             '{VOLUNTEER}')),
 jsonb_build_object('coordinatorTime', seed_labels('About 4 hours a week', 'Około 4 godzin tygodniowo'),
                    'budget', seed_labels('Small grant for equipment', 'Mały grant na sprzęt'),
                    'firstHelp', seed_labels('Within a month', 'W ciągu miesiąca')),
 NULL, 'PERSON', 'PUBLISHED', '20000000-0000-4000-8000-000000000009', '2025-03-10T09:00:00Z'),

('41000000-0000-4000-8000-000000000005', '40000000-0000-4000-8000-000000000005', 1,
 jsonb_build_array(
   seed_step(1, 'Collect donated tools', 'Zbierz podarowane narzędzia',
             'Residents donate tools they rarely use.',
             'Mieszkańcy oddają rzadko używane narzędzia.',
             '{VOLUNTEER}'),
   seed_step(2, 'Find a host', 'Znajdź gospodarza',
             'A library or community centre gives one shelf and opening hours.',
             'Biblioteka lub dom kultury udostępnia półkę i godziny otwarcia.',
             '{INSTITUTION}'),
   seed_step(3, 'Lend like books', 'Wypożyczaj jak książki',
             'Volunteers log loans in a simple sheet.',
             'Wolontariusze zapisują wypożyczenia w prostej tabeli.',
             '{VOLUNTEER_GROUP}')),
 jsonb_build_object('coordinatorTime', seed_labels('About 3 hours a week', 'Około 3 godzin tygodniowo'),
                    'budget', seed_labels('None', 'Brak'),
                    'firstHelp', seed_labels('Within a month', 'W ciągu miesiąca')),
 NULL, 'PERSON', 'PUBLISHED', '20000000-0000-4000-8000-000000000009', '2025-05-05T09:00:00Z'),

('41000000-0000-4000-8000-000000000006', '40000000-0000-4000-8000-000000000006', 1,
 jsonb_build_array(
   seed_step(1, 'Find volunteer teachers', 'Znajdź nauczycieli wolontariuszy',
             'Retired teachers offer two hours a week.',
             'Emerytowani nauczyciele oferują dwie godziny w tygodniu.',
             '{VOLUNTEER}'),
   seed_step(2, 'Use the school library', 'Skorzystaj z biblioteki szkolnej',
             'The school opens its library after classes.',
             'Szkoła otwiera bibliotekę po lekcjach.',
             '{INSTITUTION}'),
   seed_step(3, 'Match by subject', 'Dobierz według przedmiotu',
             'Children sign up for the subject they need.',
             'Dzieci zapisują się na potrzebny przedmiot.',
             '{NGO}')),
 jsonb_build_object('coordinatorTime', seed_labels('About 2 hours a week', 'Około 2 godzin tygodniowo'),
                    'budget', seed_labels('None', 'Brak'),
                    'firstHelp', seed_labels('Within 2 weeks', 'W ciągu 2 tygodni')),
 NULL, 'PERSON', 'PUBLISHED', '20000000-0000-4000-8000-000000000002', '2025-10-01T09:00:00Z'),

('41000000-0000-4000-8000-000000000007', '40000000-0000-4000-8000-000000000007', 1,
 jsonb_build_array(
   seed_step(1, 'Make a call list', 'Przygotuj listę telefonów',
             'The welfare office and neighbours suggest people who agree to calls.',
             'Ośrodek pomocy społecznej i sąsiedzi wskazują osoby, które chcą telefonów.',
             '{INSTITUTION}'),
   seed_step(2, 'Pair callers', 'Dobierz dzwoniących',
             'Each volunteer calls the same three people every week.',
             'Każdy wolontariusz co tydzień dzwoni do tych samych trzech osób.',
             '{NGO,VOLUNTEER}'),
   seed_step(3, 'Escalate worries', 'Zgłaszaj niepokojące sygnały',
             'Callers flag health or safety worries to the NGO the same day.',
             'Dzwoniący tego samego dnia zgłaszają organizacji sygnały dotyczące zdrowia lub bezpieczeństwa.',
             '{NGO}')),
 jsonb_build_object('coordinatorTime', seed_labels('About 3 hours a week', 'Około 3 godzin tygodniowo'),
                    'budget', seed_labels('Phone credit for volunteers', 'Doładowania telefonów dla wolontariuszy'),
                    'firstHelp', seed_labels('Within 1 week', 'W ciągu tygodnia')),
 NULL, 'PERSON', 'PUBLISHED', '20000000-0000-4000-8000-000000000009', '2025-02-14T09:00:00Z'),

('41000000-0000-4000-8000-000000000008', '40000000-0000-4000-8000-000000000008', 1,
 jsonb_build_array(
   seed_step(1, 'Find fixers', 'Znajdź majsterkowiczów',
             'Residents with repair skills volunteer one Saturday a month.',
             'Mieszkańcy z umiejętnościami naprawczymi poświęcają jedną sobotę w miesiącu.',
             '{VOLUNTEER_GROUP}'),
   seed_step(2, 'Book a room', 'Zarezerwuj salę',
             'A library or community centre hosts the café.',
             'Biblioteka lub dom kultury gości kawiarenkę.',
             '{INSTITUTION}'),
   seed_step(3, 'Share what was saved', 'Pokaż, co udało się uratować',
             'Post the number of repaired items after each café.',
             'Po każdym spotkaniu podaj liczbę naprawionych rzeczy.',
             '{VOLUNTEER_GROUP}')),
 jsonb_build_object('coordinatorTime', seed_labels('About 5 hours a month', 'Około 5 godzin miesięcznie'),
                    'budget', seed_labels('Spare parts, about 200 PLN a month', 'Części zamienne, około 200 zł miesięcznie'),
                    'firstHelp', seed_labels('Within a month', 'W ciągu miesiąca')),
 NULL, 'PERSON', 'PUBLISHED', '20000000-0000-4000-8000-000000000002', '2025-07-07T09:00:00Z');

UPDATE playbook p SET current_version_id = v.id
FROM playbook_version v
WHERE v.playbook_id = p.id AND v.number = 1;

-- ---------------------------------------------------------------------------
-- Cases waiting in the Riverside triage queue
-- ---------------------------------------------------------------------------
INSERT INTO case_file (id, community_id, number, kind, title, summary, category_code, urgency, status, lat, lng, area_label,
                       report_count, supporter_count, created_at, updated_at) VALUES
('50000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 412, 'NEED',
 seed_i18n('Seniors stuck in walk-up blocks', 'Seniorzy uwięzieni w blokach bez windy',
           'Літні люди не можуть вийти з будинків без ліфта'),
 seed_i18n('Residents in their 70s and 80s on upper floors of 1960s blocks without lifts rarely leave home. They ask for help with shopping, doctor visits and company. Several family members reported on behalf of parents.',
           'Mieszkańcy w wieku 70 i 80 lat z wyższych pięter bloków z lat 60. bez windy rzadko wychodzą z domu. Proszą o pomoc w zakupach, wizytach u lekarza i o towarzystwo. Kilka zgłoszeń wysłały rodziny w imieniu rodziców.',
           'Мешканці віком 70–80 років на верхніх поверхах будинків без ліфта рідко виходять з дому. Вони просять допомоги з покупками, візитами до лікаря та спілкуванням.'),
 'seniors', 'MEDIUM', 'TRIAGED', 52.2496, 21.0410, 'Elm Street area', 17, 0, now() - interval '9 days', now() - interval '1 day'),
('50000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 418, 'NEED',
 seed_i18n('Streetlight out on the riverside path', 'Nie działa latarnia na ścieżce nad rzeką'),
 seed_i18n('The only lamp on the riverside path between Elm Street and the bridge has been dark for two weeks. People avoid the path after dusk.',
           'Jedyna latarnia na ścieżce nad rzeką między ulicą Wiązową a mostem nie świeci od dwóch tygodni. Po zmroku ludzie omijają tę ścieżkę.'),
 'safety', 'HIGH', 'TRIAGED', 52.2471, 21.0452, 'Riverside path', 6, 0, now() - interval '2 days', now() - interval '2 hours'),
('50000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', 420, 'NEED',
 seed_i18n('Nowhere for teenagers to go in the evening', 'Młodzież nie ma dokąd pójść wieczorem'),
 seed_i18n('Teenagers from Birch Court hang around the bus stop in the evenings. Parents ask for a place with something to do.',
           'Nastolatki z osiedla Brzozowego wieczorami przesiadują na przystanku. Rodzice proszą o miejsce z jakimiś zajęciami.'),
 'youth', 'MEDIUM', 'TRIAGED', 52.2512, 21.0463, 'Birch Court', 3, 0, now() - interval '3 days', now() - interval '1 day'),
('50000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000001', 421, 'NEED',
 seed_i18n('Overflowing bins in Riverside Park', 'Przepełnione kosze w parku nad rzeką'),
 seed_i18n('Bins at the park entrance overflow every weekend and litter spreads to the playground.',
           'Kosze przy wejściu do parku w każdy weekend są przepełnione, a śmieci trafiają na plac zabaw.'),
 'green', 'LOW', 'TRIAGED', 52.2504, 21.0482, 'Riverside Park', 4, 0, now() - interval '6 hours', now() - interval '6 hours'),
('50000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000001', 415, 'IDEA',
 seed_i18n('Tool library in the community centre', 'Wypożyczalnia narzędzi w domu kultury'),
 seed_i18n('Residents propose a shelf of shared tools in the community centre, lent out like library books.',
           'Mieszkańcy proponują półkę wspólnych narzędzi w domu kultury, wypożyczanych jak książki z biblioteki.'),
 'ideas', 'LOW', 'TRIAGED', 52.2481, 21.0396, 'Community centre', 1, 11, now() - interval '5 days', now() - interval '2 days');

-- Old Town: the case where Stair Buddies was first used, closed and confirmed.
INSERT INTO case_file (id, community_id, number, kind, title, summary, category_code, urgency, status, lat, lng, area_label,
                       report_count, playbook_version_id, created_at, updated_at, resolved_at, closed_at) VALUES
('50000000-0000-4000-8000-000000000101', '10000000-0000-4000-8000-000000000002', 101, 'NEED',
 seed_i18n('Seniors on upper floors', 'Seniorzy na wyższych piętrach'),
 seed_i18n('Older residents of Old Town walk-ups needed help with shopping and getting out.',
           'Starsi mieszkańcy Starego Miasta z bloków bez windy potrzebowali pomocy z zakupami i wychodzeniem z domu.'),
 'seniors', 'MEDIUM', 'CLOSED', 52.2497, 21.0122, 'Old Town', 5, '41000000-0000-4000-8000-000000000001',
 '2025-04-02T09:00:00Z', '2025-09-15T09:00:00Z', '2025-09-01T09:00:00Z', '2025-09-15T09:00:00Z');

-- ---------------------------------------------------------------------------
-- Reports behind the cases (original language only; the case summary carries the translations)
-- ---------------------------------------------------------------------------
INSERT INTO report (id, community_id, author_id, case_id, kind, input_mode, status, body, lat, lng, created_at)
SELECT gen_random_uuid(), '10000000-0000-4000-8000-000000000001',
       ('20000000-0000-4000-8000-0000000001' || lpad(n::text, 2, '0'))::uuid,
       '50000000-0000-4000-8000-000000000001', 'NEED',
       CASE WHEN n % 2 = 0 THEN 'VOICE' ELSE 'TEXT' END, 'SUBMITTED',
       CASE n % 5
           WHEN 0 THEN seed_text('pl', NULL, 'Mieszkam na czwartym piętrze, w bloku nie ma windy. Od tygodni nie wychodzę z domu.')
           WHEN 1 THEN seed_text('pl', NULL, 'Mama ma 82 lata i nie da rady zejść po schodach. Potrzebuje pomocy z zakupami.')
           WHEN 2 THEN seed_text('en', 'My neighbour on the third floor cannot use the stairs any more and has nobody to shop for her.', NULL)
           WHEN 3 THEN seed_text('uk', NULL, NULL, 'Я живу на п’ятому поверсі без ліфта і не можу сама спуститися сходами.')
           ELSE seed_text('pl', NULL, 'Starsi sąsiedzi z naszej klatki nie wychodzą z domu, bo nie ma windy. Potrzebna jest pomoc przy wizytach u lekarza.')
       END,
       52.2496 + (n % 5 - 2) * 0.0004, 21.0410 + (n % 3 - 1) * 0.0005,
       now() - (n || ' hours')::interval - interval '1 day'
FROM generate_series(1, 17) AS n;

INSERT INTO report (id, community_id, author_id, case_id, kind, input_mode, status, body, lat, lng, created_at)
SELECT gen_random_uuid(), '10000000-0000-4000-8000-000000000001',
       ('20000000-0000-4000-8000-0000000001' || lpad(n::text, 2, '0'))::uuid,
       '50000000-0000-4000-8000-000000000002', 'NEED', 'TEXT', 'SUBMITTED',
       CASE n % 3
           WHEN 0 THEN seed_text('pl', NULL, 'Latarnia na ścieżce nad rzeką nie świeci od dwóch tygodni. Wieczorem jest tam zupełnie ciemno.')
           WHEN 1 THEN seed_text('en', 'The lamp on the riverside path is broken and it feels unsafe to walk there after dark.', NULL)
           ELSE seed_text('pl', NULL, 'Ciemno na ścieżce przy rzece, boję się tamtędy wracać z pracy.')
       END,
       52.2471, 21.0452, now() - (n || ' hours')::interval
FROM generate_series(1, 6) AS n;

INSERT INTO report (id, community_id, author_id, case_id, kind, input_mode, status, body, lat, lng, created_at)
SELECT gen_random_uuid(), '10000000-0000-4000-8000-000000000001',
       ('20000000-0000-4000-8000-0000000001' || lpad((n + 6)::text, 2, '0'))::uuid,
       '50000000-0000-4000-8000-000000000003', 'NEED', 'TEXT', 'SUBMITTED',
       CASE n % 3
           WHEN 0 THEN seed_text('pl', NULL, 'Wieczorami nastolatki nie mają gdzie się podziać i siedzą na przystanku.')
           WHEN 1 THEN seed_text('en', 'Teenagers in Birch Court have nothing to do in the evening. Could the school gym open?', NULL)
           ELSE seed_text('pl', NULL, 'Przydałoby się miejsce dla młodzieży z osiedla, jakieś zajęcia wieczorem.')
       END,
       52.2512, 21.0463, now() - (n || ' days')::interval
FROM generate_series(1, 3) AS n;

INSERT INTO report (id, community_id, author_id, case_id, kind, input_mode, status, body, lat, lng, created_at)
SELECT gen_random_uuid(), '10000000-0000-4000-8000-000000000001',
       ('20000000-0000-4000-8000-0000000001' || lpad((n + 9)::text, 2, '0'))::uuid,
       '50000000-0000-4000-8000-000000000004', 'NEED', 'TEXT', 'SUBMITTED',
       CASE n % 2
           WHEN 0 THEN seed_text('pl', NULL, 'Kosze przy wejściu do parku są przepełnione w każdy weekend.')
           ELSE seed_text('en', 'Rubbish bins in Riverside Park overflow every weekend and litter reaches the playground.', NULL)
       END,
       52.2504, 21.0482, now() - (n || ' hours')::interval
FROM generate_series(1, 4) AS n;

INSERT INTO report (id, community_id, author_id, case_id, kind, input_mode, status, body, lat, lng, created_at) VALUES
(gen_random_uuid(), '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000114',
 '50000000-0000-4000-8000-000000000005', 'IDEA', 'TEXT', 'SUBMITTED',
 seed_text('en', 'Could we have a shelf of shared tools in the community centre, lent out like library books?', NULL),
 52.2481, 21.0396, now() - interval '5 days');

-- ---------------------------------------------------------------------------
-- Participants: reporters follow their cases; supporters back the tool library idea
-- ---------------------------------------------------------------------------
INSERT INTO case_participant (id, case_id, user_id, role, joined_at)
SELECT gen_random_uuid(), r.case_id, r.author_id, 'REPORTER', min(r.created_at)
FROM report r
WHERE r.case_id IS NOT NULL
GROUP BY r.case_id, r.author_id;

INSERT INTO case_participant (id, case_id, user_id, role)
SELECT gen_random_uuid(), '50000000-0000-4000-8000-000000000005',
       ('20000000-0000-4000-8000-0000000001' || lpad(n::text, 2, '0'))::uuid, 'SUPPORTER'
FROM generate_series(1, 11) AS n;

INSERT INTO case_participant (id, case_id, user_id, role, outcome, outcome_at)
SELECT gen_random_uuid(), '50000000-0000-4000-8000-000000000101',
       ('20000000-0000-4000-8000-0000000002' || lpad(n::text, 2, '0'))::uuid, 'REPORTER', 'HELPED', '2025-09-10T09:00:00Z'
FROM generate_series(1, 5) AS n;

INSERT INTO case_event (id, case_id, type, visibility, data, created_at) VALUES
(gen_random_uuid(), '50000000-0000-4000-8000-000000000001', 'STATUS_CHANGED', 'PUBLIC',
 '{"from": "NEW", "to": "TRIAGED"}'::jsonb, now() - interval '9 days'),
(gen_random_uuid(), '50000000-0000-4000-8000-000000000101', 'STATUS_CHANGED', 'PUBLIC',
 '{"from": "CONFIRMED", "to": "CLOSED"}'::jsonb, '2025-09-15T09:00:00Z');

DROP FUNCTION seed_step(int, text, text, text, text, text[]);
DROP FUNCTION seed_text(text, text, text, text);
DROP FUNCTION seed_i18n(text, text, text);
DROP FUNCTION seed_labels(text, text, text);
