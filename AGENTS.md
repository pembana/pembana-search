See [CONTRIBUTING.md](CONTRIBUTING.md) for code style, commit message conventions,
pull request guidelines, and common build commands.

## Current library documentation

Use Context7 MCP to fetch current documentation whenever the user asks about a
library, framework, SDK, API, CLI tool, or cloud service, even familiar ones.
This includes API syntax, configuration, version migration, library-specific
debugging, setup instructions, and CLI usage. Prefer Context7 over web search
for library documentation, even when the answer seems familiar.

Do not use Context7 for refactoring, writing scripts from scratch, debugging
business logic, code review, or general programming concepts.

1. Start with `resolve-library-id`, supplying the library name and what to look
   up, unless the user provides an exact library ID in `/org/project` format.
2. Select the best match using name, description relevance, snippet count,
   source reputation (High/Medium preferred), and benchmark score. Try alternate
   names or queries if the results do not match. Use a version-specific ID when
   the user mentions a version.
3. Call `query-docs` with the selected library ID and a specific question scoped
   to one concept. Use separate calls for distinct concepts unless the question
   concerns how those concepts interact.
4. Answer using the fetched documentation.
