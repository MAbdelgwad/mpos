const mix = require('laravel-mix');
const webpack = require('webpack');
mix.setPublicPath('dist');
mix.js('src/index.js','js/app.js').react();
mix.webpackConfig({ plugins: [new webpack.ProvidePlugin({ _: 'lodash' })], output: { chunkFilename:'js/chunks/[name].js' } });
mix.copy('index.html','dist/index.html');
mix.copyDirectory('images','dist/images');
mix.options({processCssUrls:true});
