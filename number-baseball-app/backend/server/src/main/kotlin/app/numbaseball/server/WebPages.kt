package app.numbaseball.server

import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * Public web pages built from the release lead's texts in docs/play (bundled into the jar under /web).
 * Each document has a team memo (never published) followed by "## 공개 본문 — 한국어" and "## Public text — English";
 * only those two public parts are rendered. No cookies, no scripts, no external resources.
 */
class WebPages(private val appName: String = "Pitchcode", private val storeUrl: String? = null) {
    private val extensions = listOf(TablesExtension.create())
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer = HtmlRenderer.builder().extensions(extensions).build() // trusted repo content; anchors and forms are injected as HTML

    private class Doc(val ko: String, val en: String)

    private fun resource(name: String): String? =
        WebPages::class.java.getResourceAsStream("/web/$name")?.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun split(markdown: String): Doc {
        val koStart = markdown.indexOf("## 공개 본문")
        val enStart = markdown.indexOf("## Public text")
        fun section(from: Int, to: Int): String {
            if (from < 0) return ""
            val body = markdown.substring(markdown.indexOf('\n', from) + 1, if (to > from) to else markdown.length)
            return body.trim().removeSuffix("---").trim()
        }
        return Doc(section(koStart, enStart), section(enStart, -1))
    }

    private val docs: Map<String, Doc> = listOf("PRIVACY_POLICY.md", "TERMS_OF_USE.md", "DELETE_ACCOUNT_PAGE.md")
        .associateWith { name -> resource(name)?.let { split(it) } ?: Doc("", "") }

    /** Terms version as printed in the bundled terms ("Version 2026-11-v1"). */
    val termsVersion: String? =
        resource("TERMS_OF_USE.md")?.let { Regex("Version ([0-9A-Za-z.\\-]+)").find(it)?.groupValues?.get(1) }

    // ------------------------------------------------------------------ rendering

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** `### Title {#id}` -> heading with an id (the anchor gets a language suffix for the Korean copy). */
    private fun headingIds(md: String, lang: String): String = md.lines().joinToString("\n") { line ->
        val m = Regex("^(#{1,6})\\s+(.*?)\\s*\\{#([A-Za-z0-9_-]+)}\\s*$").find(line) ?: return@joinToString line
        val level = m.groupValues[1].length
        val id = if (lang == "en") m.groupValues[3] else "${m.groupValues[3]}-$lang"
        "<h$level id=\"$id\">${esc(m.groupValues[2])}</h$level>"
    }

    /** Placeholder lines of the deletion page ("[field] [ ] label [button]") become real forms. */
    private fun forms(md: String): String = md.lines().joinToString("\n") { line ->
        val m = Regex("^\\[(.+?)] \\[ ] (.+?) \\[(.+?)]\\s*$").find(line.trim()) ?: return@joinToString line
        val (field, label, button) = m.destructured
        val isHandle = field.contains('#')
        val action = if (isHandle) "/delete-account/request" else "/delete-account"
        val name = if (isHandle) "handle" else "code"
        val placeholder = if (isHandle) "Nickname#1234" else "XXXX-XXXX-XXXX"
        val buttonText = button.removeSuffix(" 버튼").removeSuffix(" button")
        """
        <form method="post" action="$action" class="box">
        <label>${esc(field)}<br><input name="$name" required maxlength="${if (isHandle) 40 else 20}" placeholder="$placeholder" autocomplete="off" spellcheck="false"></label>
        <label class="check"><input type="checkbox" name="confirm" value="yes" required> ${esc(label)}</label>
        <button type="submit">${esc(buttonText)}</button>
        </form>
        """.trimIndent()
    }

    private fun md(markdown: String, lang: String): String = renderer.render(parser.parse(forms(headingIds(markdown, lang))))

    private fun layout(title: String, body: String, status: String? = null): String = """
        <!doctype html>
        <html lang="ko">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="referrer" content="no-referrer">
        <title>${esc(title)}</title>
        <style>
        :root { color-scheme: light dark; --fg:#1d1d1f; --bg:#fafafa; --muted:#666; --line:#ddd; --accent:#1a5fd1; }
        @media (prefers-color-scheme: dark) { :root { --fg:#ececec; --bg:#141414; --muted:#aaa; --line:#333; --accent:#7aa7ff; } }
        body { margin:0; background:var(--bg); color:var(--fg); font:16px/1.6 system-ui,-apple-system,"Segoe UI",Roboto,"Noto Sans KR",sans-serif; }
        main { max-width:760px; margin:0 auto; padding:24px 16px 64px; }
        nav { display:flex; flex-wrap:wrap; gap:12px; font-size:15px; border-bottom:1px solid var(--line); padding-bottom:12px; }
        a { color:var(--accent); }
        h1 { font-size:1.6em; line-height:1.3; } h2 { margin-top:2em; } h3 { margin-top:1.6em; }
        table { border-collapse:collapse; width:100%; display:block; overflow-x:auto; }
        th, td { border:1px solid var(--line); padding:6px 8px; text-align:left; vertical-align:top; }
        .box { border:1px solid var(--line); border-radius:8px; padding:12px; margin:12px 0; display:grid; gap:10px; }
        input[type=text], input:not([type]) { font:inherit; padding:8px; width:100%; max-width:320px; box-sizing:border-box; }
        button { font:inherit; padding:8px 16px; border-radius:6px; border:1px solid var(--line); cursor:pointer; }
        .check { display:flex; gap:8px; align-items:flex-start; }
        .status { border-left:4px solid var(--accent); padding:8px 12px; background:rgba(127,127,127,.08); }
        hr { border:0; border-top:1px solid var(--line); margin:40px 0; }
        </style>
        </head>
        <body><main>
        <nav><a href="/">${esc(appName)}</a><a href="/privacy">개인정보처리방침 · Privacy</a><a href="/terms">이용약관 · Terms</a><a href="/delete-account">계정 삭제 · Delete account</a></nav>
        ${status?.let { "<p class=\"status\" role=\"status\">$it</p>" } ?: ""}
        $body
        </main></body></html>
    """.trimIndent()

    private fun bilingual(name: String, title: String, preferEnglish: Boolean, status: String? = null): String {
        val d = docs.getValue(name)
        val ko = "<section lang=\"ko\" id=\"ko\">${md(d.ko, "ko")}</section>"
        val en = "<section lang=\"en\" id=\"en\">${md(d.en, "en")}</section>"
        val switch = "<p><a href=\"#ko\">한국어</a> · <a href=\"#en\">English</a></p>"
        return layout(title, switch + (if (preferEnglish) en + "<hr>" + ko else ko + "<hr>" + en), status)
    }

    fun privacy(preferEnglish: Boolean) = bilingual("PRIVACY_POLICY.md", "$appName — 개인정보처리방침 / Privacy Policy", preferEnglish)
    fun terms(preferEnglish: Boolean) = bilingual("TERMS_OF_USE.md", "$appName — 이용약관 / Terms of Use", preferEnglish)
    fun deleteAccount(preferEnglish: Boolean, status: String? = null) =
        bilingual("DELETE_ACCOUNT_PAGE.md", "$appName — 계정 삭제 / Delete account", preferEnglish, status)

    fun landing(): String = layout(
        appName,
        """
        <h1>${esc(appName)}</h1>
        <p lang="ko">조용하고 빠른 숫자야구 대결 게임입니다. 컴퓨터, 온라인 1:1, 팀전을 즐겨 보세요.</p>
        <p lang="en">A quiet, quick number-guessing duel game: play the computer, a 1:1 match online, or team play.</p>
        ${storeUrl?.let { "<p><a href=\"${esc(it)}\">Google Play</a></p>" } ?: ""}
        <ul>
        <li><a href="/privacy">개인정보처리방침 / Privacy Policy</a></li>
        <li><a href="/terms">이용약관 / Terms of Use</a> (<a href="/terms#child-safety">아동 안전 / Child safety</a>)</li>
        <li><a href="/delete-account">계정 삭제 / Delete your account</a></li>
        </ul>
        """.trimIndent(),
    )

    // Result messages of the deletion forms (both languages).
    val deletedStatus = "계정과 전적이 삭제되었습니다. / Your account and record have been deleted."
    val badCodeStatus = "코드가 올바르지 않거나 만료되었습니다. 앱에서 새 코드를 받아 다시 시도하세요. / The code is wrong or expired. Get a new code in the app and try again."
    val confirmStatus = "확인란에 체크해 주세요. 아무것도 삭제되지 않았습니다. / Please tick the confirmation box. Nothing was deleted."
    val requestStatus = "요청을 받았습니다. 입력한 이름의 계정이 있으면 30일 뒤 삭제되며, 그 사이 앱에서 계정을 쓰면 요청은 취소됩니다. / Request received. If an account with that name exists, it will be deleted in 30 days unless it is used in the app before then."
    val rateStatus = "요청이 너무 많습니다. 잠시 후 다시 시도하세요. / Too many attempts. Please try again later."
}
