export const routeToOrders = (id: number) =>
    axios.get(`http://order-service/api/orders/${id}`);
