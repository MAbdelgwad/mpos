const mix = require('laravel-mix');
const webpack = require('webpack');
mix.setPublicPath('spring-frontend/dist');
mix.js('resources/pos/src/index.js','js/app.js').react();
mix.webpackConfig({ plugins: [new webpack.ProvidePlugin({ _: 'lodash' })], output: { chunkFilename:'js/chunks/[name].js' } });
mix.copy('spring-frontend/index.html','spring-frontend/dist/index.html');
mix.copyDirectory('resources/images','spring-frontend/dist/images');
mix.options({processCssUrls:true});
