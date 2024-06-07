# Building and serving documentation locally

## Executing within source code

(Clone `https://github.com/apache/james-project` locally, go into `docs` folder)

**Step 1**: [Install Antora](https://docs.antora.org/antora/latest/install-and-run-quickstart/)

**Step 2**: Build the Antora content locally

```
antora antora-playbook-local.yml
```

**Step 3**: Open `build/site/index.html` in your browser.

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
