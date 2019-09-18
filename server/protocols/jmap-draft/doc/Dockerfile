FROM iron/python:2-dev

RUN pip install mkdocs

COPY . /doc

RUN find . -name "*.mdwn" | while IFS= read -r f; do mv -v "$f" "$(echo "$f" | sed -e 's/.mdwn$/.md/' - )"; done

RUN mv /doc/specs/spec/apimodel.md /doc/specs/index.md

WORKDIR /doc

RUN mkdocs build --clean

WORKDIR site

EXPOSE 8000

CMD python -m SimpleHTTPServer 8000