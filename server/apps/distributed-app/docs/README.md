# Building and serving documentation locally

## Executing within source code

(Clone `https://github.com/apache/james-project` locally, go into `server/apps/distributed-app/docs` folder)

**Step 1**: [Install Antora](https://docs.antora.org/antora/2.3/install/install-antora/)

**Step 2**: Build the Antora content locally

```
antora antora-playbook-local.yml
```

**Step 3**: Open `build/site/index.html` in your browser.

## Building from the ZIP package

[Install Antora](https://docs.antora.org/antora/2.3/install/install-antora/).

Unzip `james-server-distributed-app.zip`.

Go in the `docs` folder.

Executing Antora can only be done from within a git repository.

You will need to initialize the git repository:

```
$ git init
$ git add .
$ git commit -m "First commit"
```

Then adapt `antora-playbook-local.yml` to match the git location:

```
site:
  title: Apache James Distributed Server
  url: https://james.apache.org/
  start_page: james-distributed-app::index.adoc
content:
  sources:
    - url: ./
      branches: HEAD
      start_path: ./
ui:
  bundle:
    url: https://gitlab.com/antora/antora-ui-default/-/jobs/artifacts/master/raw/build/ui-bundle.zip?job=bundle-stable
  supplemental_files: ./ui-overrides
runtime:
  fetch: true
```

Build the Antora content locally

```
antora antora-playbook-local.yml
```

Open `build/site/index.html` in your browser.

## Building with Dockerfile

To build the document from apache-james repository, you can use the Dockerfile provided in this folder.

Build the Docker image:

```
docker build --build-arg JAMES_CHECKOUT=master -f Dockerfile -t james-site-antora .
```

Then run the Docker image:

```
docker run -p 80:80 james-site-antora
```

Go to `http://localhost` in your browser.
