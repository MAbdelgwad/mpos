// Serialize using the installed Axios contract, then encode brackets in the query only.
// Axios 0.25 deliberately emits literal brackets, which Tomcat rejects in request targets.
function prepareQueryUrl(config, client) {
    const url = client.getUri(config);
    const query = url.indexOf('?');
    if (query !== -1) {
        const fragment = url.indexOf('#', query);
        const end = fragment === -1 ? url.length : fragment;
        config.url = url.slice(0, query + 1) + url.slice(query + 1, end)
            .replace(/\[/g, '%5B').replace(/\]/g, '%5D') + url.slice(end);
    } else {
        config.url = url;
    }
    // Params have already been serialized; prevent the adapter from appending them twice.
    config.params = undefined;
    return config;
}
module.exports = {prepareQueryUrl};
