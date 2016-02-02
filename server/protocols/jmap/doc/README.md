Annotated JMAP documentation
============================

This directory contains annotated jmap documentation as found on http://jmap.io/.

Annotations aim at tracking implementation progress in James project.

You can read it as an html site using the provided Dockerfile.

̀
$ docker build --tag=mkdocs .
$ docker run --rm -p 8000:8000 mkdocs
̀

Then open http://localhost:8000/ in your browser.
