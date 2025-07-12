module.exports = {
  hooks: {
    readPackage(pkg) {
      const allowed = ['@parcel/watcher', '@swc/core', 'lmdb', 'msgpackr-extract']
      if (pkg.scripts && !allowed.includes(pkg.name)) {
        delete pkg.scripts
      }
      return pkg
    },
  },
}