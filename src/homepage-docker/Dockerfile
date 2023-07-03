FROM ruby:2.6.8-buster

RUN apt-get update \
  && apt-get install -y \
    nodejs \
    python-pygments \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/

RUN gem install \
  github-pages \
  jekyll \
  jekyll-feed \
  jekyll-redirect-from \
  jekyll-seo-tag \
  kramdown \
  rdiscount \
  rouge

# Copy the script
COPY compile.sh /root/compile.sh

# Define the entrypoint
WORKDIR /james-project
ENTRYPOINT ["/root/compile.sh"]
