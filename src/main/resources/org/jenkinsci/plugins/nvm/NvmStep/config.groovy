package org.jenkinsci.plugins.nvm.NvmStep

import lib.LayoutTagLib


l = namespace(LayoutTagLib)
t = namespace('/lib/hudson')
st = namespace('jelly:stapler')
f = namespace('/lib/form')

f.entry(title: 'Version', field: 'version') {
  f.textbox()
}
