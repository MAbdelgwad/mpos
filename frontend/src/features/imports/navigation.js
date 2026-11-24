export function shipmentMenuTarget(target, location) {
    const root = '/app/import-shipments';
    const within = path => path === root || path.startsWith(root + '/');
    if (!within(target) || !within(location.pathname)) return target;
    const id = new URLSearchParams(location.search).get('shipment');
    return id ? target + '?shipment=' + encodeURIComponent(id) : target;
}
