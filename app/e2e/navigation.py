"""Every way of moving around must show the page the address bar names: typed and pasted links,
in-app links, the bottom and panel navigation, and the browser's back and forward buttons.

    python app/e2e/navigation.py                          # against http://localhost:8080
    BASE=http://localhost:5173 python app/e2e/navigation.py
"""
import os
import re
import sys

from playwright.sync_api import Page, sync_playwright

BASE = os.environ.get("BASE", "http://localhost:8080").rstrip("/")
PHONE = {"width": 390, "height": 844}
problems: list[str] = []


def check(page: Page, label: str, path: str, expected: str):
    """expected is visible text, or css=<selector> for an element on the page being shown."""
    page.wait_for_timeout(1200)
    url = page.url.replace(BASE, "")
    target = page.locator(expected[4:]) if expected.startswith("css=") else page.get_by_text(expected)
    ok = url.startswith(path) and target.locator("visible=true").count() > 0
    if not ok:
        problems.append(f"{label} (at {url})")
    print(f"{'ok ' if ok else 'BAD'} {label:30} {url}")


def phone(browser, login: str) -> Page:
    ctx = browser.new_context(viewport=PHONE)
    ctx.route(re.compile(r".*tile\.openstreetmap\.org.*"), lambda r: r.abort())
    page = ctx.new_page()
    page.goto(f"{BASE}/signin")
    page.get_by_role("button", name=re.compile("^" + re.escape(login))).first.click()
    return page


with sync_playwright() as p:
    browser = p.chromium.launch()
    shown = ".ion-page:not(.ion-page-hidden)"

    maria = phone(browser, "Maria")
    check(maria, "sign in -> map", "/home", "Twoja okolica")
    maria.locator(f"{shown} .fab").click()
    check(maria, "report button", "/report/new", "Co chcesz zgłosić?")
    maria.goto(f"{BASE}/my")
    check(maria, "typed address /my", "/my", "Moje zgłoszenia")
    maria.go_back()
    check(maria, "browser back", "/report/new", "Co chcesz zgłosić?")
    maria.go_forward()
    check(maria, "browser forward", "/my", "Moje zgłoszenia")
    maria.locator(f"{shown} .bottom-nav a[href='/ideas']").click()
    check(maria, "bottom nav -> ideas", "/ideas", "Pomysły, które zadziałały")
    maria.locator(f"{shown} .bottom-nav a[href='/home']").click()
    check(maria, "bottom nav -> map", "/home", "Twoja okolica")
    maria.locator(f"{shown} .card__title a").first.click()
    check(maria, "card link -> case", "/case/", f"css={shown} .stepper")
    maria.go_back()
    check(maria, "browser back -> map", "/home", "Twoja okolica")
    maria.goto(f"{BASE}/playbooks/stair-buddies")
    check(maria, "pasted playbook link", "/playbooks/stair-buddies", "Co zadziałało")

    ewa = phone(browser, "Ewa N.")
    check(ewa, "moderator on a phone", "/moderation", "Triage queue")
    ewa.locator(".mobile-panel-bar a[href='/playbooks']").click()
    check(ewa, "panel bar -> playbooks", "/playbooks", "Proven solutions this community can use")
    ewa.locator(".mobile-panel-bar a[href='/moderation']").click()
    check(ewa, "panel bar -> queue", "/moderation", "Triage queue")
    browser.close()

if problems:
    print("\nWrong page shown:", *problems, sep="\n  ")
    sys.exit(1)
print("\nEvery route shows the right page.")
