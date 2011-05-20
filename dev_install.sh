set -ex

reefdir=totalgrid-reef-*

$reefdir/bin/stop > /dev/null 2>&1 || true

rm -rf $reefdir
tar -xvf assembly/target/totalgrid-reef*.tar.gz

current=`pwd`

cd $reefdir && ./install_reef.sh samples/integration/config.xml
