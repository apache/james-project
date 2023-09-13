This is the base Jekyll theme. You can find out more info about customizing your Jekyll theme, as well as basic Jekyll usage documentation at [jekyllrb.com](http://jekyllrb.com/)

You can find the source code for the Jekyll new theme at:
{% include icon-github.html username="jekyll" %} /
[minima](https://github.com/jekyll/minima)

You can find the source code for Jekyll at
{% include icon-github.html username="jekyll" %} /
[jekyll](https://github.com/jekyll/jekyll)


## How to post new articles on Jekyll


#### The Posts Folder

As explained on the [directory structure](https://jekyllrb.com/docs/structure/) page, the _posts folder is where your blog posts will live:

```
.
├── _drafts
├── _includes
├── _layouts
├── _posts
|   ├── 2007-10-29-why-every-programmer-should-play-nethack.md
|   └── 2009-04-26-barcamp-boston-4-roundup.md
├── _data
├── _site
├── .jekyll-metadata
└── index.html
```

The files extension is Markdown.


#### Front Matter


The front matter is where Jekyll starts to get really cool. Any file that contains a YAML front matter block will be processed by Jekyll as a special file. The front matter must be the first thing in the file and must take the form of valid YAML set between triple-dashed lines. Here is a basic example:

```
---
layout: post
title:  "Welcome to Jekyll"
date:   2016-08-29 16:13:22 +0200
tags: 
---
```

* ```Layout``` attribute must be always Post to load the good HTM-CSS template of the page
* ```Title``` attribute is free
* ```Date``` attribute must be formatted like this: ```YYYY-MM-DD HH:MM:SS +/-TTTT```
* ```tags``` attributes can be added to a post. Tags can be specified as a [YAML list](https://en.wikipedia.org/wiki/YAML#Basic_components) or a space-separated string

#### Warning!
**The value is conditioning the display or not of the post at compilation, so is post date means you must build site with jekyll after it to actually publish it.**

No Content Management System here who's publish for you the post.
