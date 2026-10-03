"""Maria's case, clicked through in a real browser by every role, with a screenshot of each screen.

Needs a freshly started app with the demo data (DEMO_RESET=true, the docker compose default):
    pip install playwright && python -m playwright install chromium
    python app/e2e/click_through.py                       # against http://localhost:8080
    BASE=http://localhost:5173 python app/e2e/click_through.py    # against the Vite dev server
Screenshots go to e2e-shots/ (OUT=... to change). Map tiles are not loaded unless TILES=1.
Exits non-zero if a step fails or a page logs an error.
"""
import os
import re
import sys

from playwright.sync_api import Browser, Page, expect, sync_playwright

BASE = os.environ.get("BASE", "http://localhost:8080").rstrip("/")
OUT = os.environ.get("OUT", "e2e-shots")
TILES = os.environ.get("TILES") == "1"
PHONE = {"width": 390, "height": 844}
DESKTOP = {"width": 1440, "height": 960}
PEOPLE = {"maria": "Maria", "ewa": "Ewa N.", "kasia": "Kasia L.", "piotr": "Piotr K.", "ola": "Ola M"}
errors: list[str] = []


def shot(page: Page, name: str, full: bool = False):
    """Waits for live notices (toasts) to go away first, so the screenshot shows the page itself."""
    try:
        page.wait_for_function("() => !document.querySelector('ion-toast')", timeout=8000)
    except Exception:
        pass
    page.wait_for_timeout(400)
    page.screenshot(path=f"{OUT}/{name}.png", full_page=full)
    print("  shot", name)


def is_noise(url: str, failure: str | None) -> bool:
    """Blocked map tiles, and the event stream a full page load cuts off, are expected."""
    return "tile.openstreetmap" in url or ("/api/stream" in url and "ERR_ABORTED" in (failure or ""))


def open_as(browser: Browser, login: str, viewport) -> Page:
    ctx = browser.new_context(viewport=viewport, device_scale_factor=1, locale="en-GB")
    if not TILES:
        ctx.route(re.compile(r".*tile\.openstreetmap\.org.*"), lambda route: route.abort())
    page = ctx.new_page()
    page.on("pageerror", lambda e: errors.append(f"{login}: {e}"))
    page.on("console", lambda m: m.type == "error" and "Failed to load resource" not in m.text
            and errors.append(f"{login} console: {m.text}"))
    page.on("requestfailed", lambda r: not is_noise(r.url, r.failure)
            and errors.append(f"{login} request failed: {r.url} {r.failure}"))
    page.on("response", lambda r: r.status >= 400 and errors.append(f"{login} HTTP {r.status}: {r.url}"))
    page.goto(f"{BASE}/signin")
    page.get_by_role("button", name=re.compile("^" + re.escape(PEOPLE[login]))).first.click()
    page.wait_for_timeout(1200)
    return page


def visible(page: Page, text):
    return page.get_by_text(text).locator("visible=true").first


def current(page: Page, selector: str):
    """The element on the page Ionic is showing (hidden pages stay in the DOM)."""
    return page.locator(f".ion-page:not(.ion-page-hidden) {selector}")


def run(browser: Browser):
    print("1. Sign-in screen")
    page = browser.new_context(viewport=DESKTOP).new_page()
    page.goto(f"{BASE}/signin")
    page.wait_for_timeout(800)
    shot(page, "01-signin")
    page.context.close()

    print("2. Maria (Polish, phone): the map of her neighbourhood")
    maria = open_as(browser, "maria", PHONE)
    expect(visible(maria, "Twoja okolica")).to_be_visible(timeout=10000)
    maria.wait_for_timeout(1000)
    shot(maria, "02-home-maria")

    print("3. Maria reports her neighbour's problem")
    current(maria, ".fab").click()
    expect(visible(maria, "Co chcesz zgłosić?")).to_be_visible(timeout=10000)
    current(maria, "textarea").fill(
        "Moja sąsiadka z czwartego piętra ma 84 lata i od miesiąca nie wychodzi z domu, bo w bloku nie ma windy. "
        "Ktoś musiałby jej pomagać z zakupami."
    )
    current(maria, "input[aria-label='Adres']").fill("Wiązowa 4")
    shot(maria, "03-new-report")
    maria.get_by_role("button", name="Dalej").click()

    print("4. The AI's reading, and a similar case nearby")
    expect(visible(maria, "Podobna sprawa w pobliżu")).to_be_visible(timeout=20000)
    shot(maria, "04-review", full=True)
    maria.get_by_role("button", name="Dołącz do tej sprawy").click()

    print("5. Maria follows the case she joined")
    expect(visible(maria, "Twoja sprawa")).to_be_visible(timeout=10000)
    shot(maria, "05-case-maria")

    print("6. Ewa (moderator, English, desktop) approves the AI's proposal")
    ewa = open_as(browser, "ewa", DESKTOP)
    expect(visible(ewa, "Triage queue")).to_be_visible(timeout=10000)
    ewa.get_by_role("button", name=re.compile("Seniors stuck in walk-up blocks")).first.click()
    expect(visible(ewa, "Approve and assign")).to_be_visible(timeout=10000)
    ewa.wait_for_timeout(800)
    shot(ewa, "06-queue-ewa", full=True)
    ewa.get_by_role("button", name="Approve and assign").click()
    ewa.wait_for_timeout(1500)
    shot(ewa, "07-queue-after-approve")

    print("7. Kasia (doer, phone) accepts, works through her steps and resolves the case")
    kasia = open_as(browser, "kasia", PHONE)
    expect(visible(kasia, "Twoje sprawy")).to_be_visible(timeout=10000)
    kasia.wait_for_timeout(800)
    shot(kasia, "08-doer-offer", full=True)
    kasia.get_by_role("button", name="Przyjmij").first.click()
    expect(visible(kasia, "Twoje kroki")).to_be_visible(timeout=10000)
    for box in current(kasia, ".check input[type=checkbox]").all():
        box.check()
        kasia.wait_for_timeout(300)
    current(kasia, "textarea").first.fill(
        "Wywieście listę chętnych na drzwiach każdej klatki. Zgłosiło się 9 sąsiadów, więcej niż z ulotek. "
        "Wszyscy seniorzy z Wiązowej 4 i 6 mają już pomocnika do zakupów i wizyt u lekarza."
    )
    kasia.get_by_text("To rozwiązuje sprawę").click()
    shot(kasia, "09-doer-active", full=True)
    kasia.get_by_role("button", name="Wyślij i zamknij").click()
    kasia.wait_for_timeout(1500)
    shot(kasia, "10-doer-after-send")

    print("8. Maria's phone asks whether it helped (live, no reload)")
    expect(maria.get_by_role("button", name="Tak, pomogło")).to_be_visible(timeout=15000)
    shot(maria, "11-case-confirm")
    maria.get_by_role("button", name="Tak, pomogło").click()
    maria.wait_for_timeout(2500)
    shot(maria, "12-case-closed", full=True)

    print("9. Ewa reviews and publishes the AI's next playbook version")
    ewa.goto(f"{BASE}/playbooks/stair-buddies")
    expect(visible(ewa, "Publish version 2")).to_be_visible(timeout=20000)
    ewa.wait_for_timeout(600)
    shot(ewa, "13-playbook-draft", full=True)
    ewa.get_by_role("button", name="Publish version 2").click()
    ewa.wait_for_timeout(1500)
    shot(ewa, "14-playbook-published")

    print("10. Piotr (admin): community setup as data")
    piotr = open_as(browser, "piotr", DESKTOP)
    piotr.goto(f"{BASE}/admin")
    expect(visible(piotr, "Community setup")).to_be_visible(timeout=10000)
    piotr.wait_for_timeout(800)
    shot(piotr, "15-admin", full=True)

    print("11. Ewa on a phone: the panel becomes a top bar")
    ewa_phone = open_as(browser, "ewa", PHONE)
    ewa_phone.wait_for_timeout(1200)
    shot(ewa_phone, "16-queue-phone")

    print("12. Maria: her reports, the ideas library, a playbook, her profile")
    for path, text, name in [
        ("/my", "Moje zgłoszenia", "17-my-reports"),
        ("/ideas", "Pomysły, które zadziałały", "18-ideas"),
        ("/playbooks/stair-buddies", "Co zadziałało", "19-playbook-resident"),
        ("/profile", "Twoje role", "20-profile"),
    ]:
        maria.goto(f"{BASE}{path}")
        expect(visible(maria, text)).to_be_visible(timeout=10000)
        maria.wait_for_timeout(600)
        shot(maria, name)

    print("13. Ola (Ukrainian) on the same map")
    ola = open_as(browser, "ola", PHONE)
    expect(visible(ola, "Ваш район")).to_be_visible(timeout=10000)
    ola.wait_for_timeout(800)
    shot(ola, "21-home-ola-uk")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    with sync_playwright() as p:
        browser = p.chromium.launch()
        try:
            run(browser)
        finally:
            browser.close()
    if errors:
        print("\nPage errors:")
        for e in errors:
            print(" ", e)
        sys.exit(1)
    print(f"\nAll steps passed. Screenshots in {OUT}/")
