import os

# -- Project information -----------------------------------------------------

project = "Sniffer"
copyright = "2026, Bookshelf Contributors"  # noqa: A001
author = "Bookshelf Contributors"

# -- General configuration ---------------------------------------------------

extensions = [
    "myst_parser",
    "sphinx_copybutton",
    "sphinx_design",
    "sphinx_minecraft",
]

exclude_patterns = ["_build", "Thumbs.db", ".DS_Store"]
# Changelog entries start at H2, like Bookshelf does, so their title is the version itself.
suppress_warnings = ["misc.highlighting_failure", "myst.header"]
templates_path = ["_templates"]

# -- MyST options ------------------------------------------------------------

myst_heading_anchors = 6
myst_enable_extensions = [
    "amsmath",
    "colon_fence",
    "deflist",
    "dollarmath",
    "fieldlist",
    "html_admonition",
    "html_image",
    "linkify",
    "replacements",
    "smartquotes",
    "strikethrough",
    "substitution",
    "tasklist",
]

# -- Options for HTML output -------------------------------------------------

html_baseurl = os.environ.get("READTHEDOCS_CANONICAL_URL", "")
html_theme = "breeze"
html_title = "Sniffer"
html_logo = "_static/logo-sniffer.png"
html_favicon = "_static/logo-sniffer.png"

html_static_path = ["_static"]
html_css_files = ["sniffer.css"]

html_context = {
    "READTHEDOCS": os.environ.get("READTHEDOCS", "") == "True",
    "github_user": "mcbookshelf",
    "github_repo": "sniffer",
    "github_version": "master",
    "doc_path": "docs",
}

html_theme_options = {
    "emojis_sidebar_nav": True,
    "footer": [
        "footer-copyright.html",
        "footer-links.html",
        "external-links.html",
    ],
    "external_links": [
        "https://discord.gg/MkXytNjmBt",
        "https://github.com/mcbookshelf/sniffer",
    ],
}
