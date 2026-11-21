package app.numbaseball.server

import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * The static pages Google needs a public URL for (the app has no website): /privacy, /terms, /delete-account.
 * They are rendered from the release lead's texts in docs/play (bundled into the jar under /web). Each document has a
 * team memo (never published) followed by "## 공개 본문 — 한국어" and "## Public text — English"; only those two public
 * parts are rendered. Plain text pages: no forms, no cookies, no scripts, no external resources.
 */
class WebPages {
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

    /** Old form placeholders ("[field] [ ] label [button]") are dropped: the pages have no forms. */
    private fun dropFormPlaceholders(md: String): String =
        md.lines().filterNot { Regex("^\\[(.+?)] \\[ ] (.+?) \\[(.+?)]\\s*$").matches(it.trim()) }.joinToString("\n")

    private fun md(markdown: String, lang: String): String = renderer.render(parser.parse(dropFormPlaceholders(headingIds(markdown, lang))))

    private fun layout(title: String, body: String, lang: String): String = """
        <!doctype html>
        <html lang="$lang">
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
        hr { border:0; border-top:1px solid var(--line); margin:40px 0; }
        </style>
        </head>
        <body><main>
        <nav><a href="/privacy">개인정보처리방침 · Privacy</a><a href="/terms">이용약관 · Terms</a><a href="/delete-account">계정 삭제 · Delete account</a></nav>
        $body
        </main></body></html>
    """.trimIndent()

    private fun bilingual(name: String, title: String, preferEnglish: Boolean): String {
        val d = docs.getValue(name)
        val ko = "<section lang=\"ko\" id=\"ko\">${md(d.ko, "ko")}</section>"
        val en = "<section lang=\"en\" id=\"en\">${md(d.en, "en")}</section>"
        val switch = "<p><a href=\"#ko\">한국어</a> · <a href=\"#en\">English</a></p>"
        return layout(title, switch + (if (preferEnglish) en + "<hr>" + ko else ko + "<hr>" + en), if (preferEnglish) "en" else "ko")
    }

    fun privacy(preferEnglish: Boolean) = bilingual("PRIVACY_POLICY.md", "개인정보처리방침 / Privacy Policy", preferEnglish)
    fun terms(preferEnglish: Boolean) = bilingual("TERMS_OF_USE.md", "이용약관 / Terms of Use", preferEnglish)
    fun deleteAccount(preferEnglish: Boolean) = bilingual("DELETE_ACCOUNT_PAGE.md", "계정 삭제 / Delete your account", preferEnglish)
}
