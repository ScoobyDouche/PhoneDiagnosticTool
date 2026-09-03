# Release notes

One file per release, named `v<MAJOR>.<MINOR>.<PATCH>.md`.

The `Release` workflow uses the matching file as the GitHub Release body. If the
first line is a level-one heading (`# ...`) it becomes the release **title** and
is stripped from the body, so it is not repeated.

When no file exists for the version being released, the workflow falls back to
the `## [<version>]` section of [`CHANGELOG.md`](../../CHANGELOG.md), and failing
that to GitHub's auto-generated notes. Writing a file here is therefore optional
— it is the place to say more than a changelog entry should.
