import React from "react";
import { Route, Navigate, Routes } from "react-router-dom";
import "../../pos/src/assets/sass/style.react.scss";
import { Tokens } from "./constants";
import { ProtectedRoute } from "./shared/sharedMethod";
import { route } from "./routes";
import TopProgressBar from "./shared/components/loaders/TopProgressBar";
import { useSelector } from "react-redux";

function AdminApp(props) {
    const { config } = props;
    const token = localStorage.getItem(Tokens.ADMIN);
    const { allConfigData } = useSelector((state) => state);

    const prepareRoutes = (config) => {
        const permissions = config;
        let filterRoutes = [];
        route.forEach((route) => {
            if (window.MPOS_SPRING && (!/^(dashboard|brands|warehouse|product-categories|units|base-units|suppliers|customers|users|products|roles|settings|purchases|inventory)(\/|$)/.test(route.path) || route.path.startsWith('warehouse/detail/') || route.path.startsWith('purchases/'))) return;
            if (
                (permissions && permissions.indexOf(route.permission) !== -1) ||
                route.permission === ""
            ) {
                filterRoutes.push(route);
            }
        });
        return filterRoutes;
    };

    if (!allConfigData?.version && token !== null) {
        return <TopProgressBar />;
    }

    const routes = config && prepareRoutes(config);

    return (
        <Routes>
            {routes.map((route, index) => {
                return route.ele ? (
                    <Route
                        key={index}
                        exact={true}
                        path={route.path}
                        element={
                            token !== null ? (
                                <ProtectedRoute
                                    allConfigData={allConfigData}
                                    route={route.path}
                                >
                                    {route.ele}
                                </ProtectedRoute>
                            ) : (
                                <Navigate replace to={"/login"} />
                            )
                        }
                    />
                ) : null;
            })}
            <Route path="*" element={<Navigate replace to={"/"} />} />
        </Routes>
    );
}

export default AdminApp;
