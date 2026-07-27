(function () {
  var DESIGN_WIDTH = 1280

  function installScaleRoot() {
    if (document.querySelector('.guide-scale-root')) return document.querySelector('.guide-scale-root')

    var root = document.createElement('div')
    root.className = 'guide-scale-root'

    while (document.body.firstChild) {
      root.appendChild(document.body.firstChild)
    }
    document.body.appendChild(root)
    return root
  }

  function applyScale(root) {
    var viewportWidth = Math.max(document.documentElement.clientWidth || 0, window.innerWidth || 0)
    var scale = Math.min(1, viewportWidth / DESIGN_WIDTH)
    root.style.width = DESIGN_WIDTH + 'px'
    root.style.transform = 'scale(' + scale + ')'
    document.body.style.height = Math.ceil(root.scrollHeight * scale) + 'px'
  }

  function boot() {
    var root = installScaleRoot()
    var update = function () { applyScale(root) }
    update()
    window.addEventListener('resize', update)
    window.addEventListener('load', update)
    setTimeout(update, 100)
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot)
  } else {
    boot()
  }
})()
