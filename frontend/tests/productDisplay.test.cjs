const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const babel = require('@babel/core');
const React = require('react');
const {renderToStaticMarkup} = require('react-dom/server');
const router = require('react-router-dom');
const empty = () => null;
function compile(file, mocks) {
    const code = babel.transformSync(fs.readFileSync(path.join(__dirname, file), 'utf8'), {
        babelrc: false, configFile: false, presets: [require.resolve('@babel/preset-react')],
        plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')]
    }).code;
    const exports = {};
    vm.runInNewContext(code, {exports, window: {MPOS_SPRING: true}, localStorage: {getItem: () => 'test-token'}, require: name => {
        if (name in mocks) return mocks[name];
        throw Error('Unexpected import ' + name);
    }});
    return exports.default;
}
function productPage(frontSetting, products) {
    let rows;
    const Product = compile('../src/components/product/Product.js', {
        react: React, 'react-redux': {connect: () => component => component}, moment: require('moment'),
        'react-bootstrap-v5': {Button: empty, Image: empty}, '../MasterLayout': ({children}) => children,
        '../../store/action/productAction': {}, '../../shared/table/ReactDataTable': props => {rows = props.items; return null;},
        './DeleteProduct': empty, '../../shared/tab-title/TabTitle': empty, './ProductImageLightBox': empty,
        '../../assets/images/brand_logo.png': '', '../../shared/sharedMethod': {
            getFormattedDate: () => '', getFormattedMessage: key => key, placeholderText: key => key, currencySymbolHendling: () => ''
        }, '../../shared/action-buttons/ActionButton': empty, '../../store/action/frontSettingAction': {},
        '../../shared/components/loaders/TopProgressBar': empty, './ImportProductModel': empty,
        '../../store/action/productExcelAction': {}
    });
    renderToStaticMarkup(React.createElement(Product, {products, frontSetting, allConfigData: {}, totalRecord: products?.length || 0}));
    return rows;
}
const products = [{id: 7, attributes: {name: '[تجريبي] أرز', code: 'DEMO-P001', product_price: 10, in_stock: 72}}];
test('product table renders products before settings load and with empty currency', () => {
    for (const settings of [undefined, {}, {value: {currency_symbol: ''}}, {value: {currency_symbol: 'SAR'}}]) {
        const rows = productPage(settings, products);
        assert.equal(rows.length, 1);
        assert.equal(rows[0].code, 'DEMO-P001');
        assert.equal(rows[0].in_stock, 72);
        assert.equal(rows[0].currency, settings?.value?.currency_symbol || '');
    }
});
test('empty product responses remain an empty array', () => {
    assert.equal(productPage({}, []).length, 0);
    assert.equal(productPage({}, undefined).length, 0);
});
function renderRoute(initialEntry, permissions) {
    const AdminApp = compile('../src/AdminApp.js', {
        react: React, 'react-router-dom': router, './assets/sass/style.react.scss': {},
        './constants': {Tokens: {ADMIN: 'token'}}, './shared/sharedMethod': {ProtectedRoute: ({children}) => children},
        './routes': {route: [
            {path: 'print/barcode', ele: React.createElement('span', null, 'BARCODE_PAGE'), permission: 'manage_products'},
            {path: 'base-units', ele: React.createElement('span', null, 'BASE_UNITS_PAGE'), permission: 'manage_units'},
            {path: 'pos', ele: React.createElement('span', null, 'POS_PAGE'), permission: 'manage_pos'}
        ]}, './shared/components/loaders/TopProgressBar': empty,
        'react-redux': {useSelector: () => ({allConfigData: {version: 'test'}})}
    });
    return renderToStaticMarkup(React.createElement(router.MemoryRouter, {initialEntries: [initialEntry]},
        React.createElement(AdminApp, {config: permissions})));
}
test('Spring barcode route is reachable with product permission', () => {
    assert.match(renderRoute('/print/barcode', ['manage_products']), /BARCODE_PAGE/);
});
test('barcode permission and unsupported Spring routes remain restricted', () => {
    assert.doesNotMatch(renderRoute('/print/barcode', []), /BARCODE_PAGE/);
    assert.doesNotMatch(renderRoute('/pos', ['manage_pos']), /POS_PAGE/);
});

test('removed base-unit screen cannot be opened in Spring mode', () => {
    assert.doesNotMatch(renderRoute('/base-units', ['manage_units']), /BASE_UNITS_PAGE/);
});
