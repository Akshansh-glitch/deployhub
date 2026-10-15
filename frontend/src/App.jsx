import { Navigate, Route, Routes } from "react-router-dom";
import { auth } from "./api.js";
import Layout from "./components/Layout.jsx";
import Login from "./pages/Login.jsx";
import AppsList from "./pages/AppsList.jsx";
import NewApp from "./pages/NewApp.jsx";
import AppDetail from "./pages/AppDetail.jsx";

function RequireAuth({ children }) {
  return auth.token ? children : <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<AppsList />} />
        <Route path="apps/new" element={<NewApp />} />
        <Route path="apps/:id" element={<AppDetail />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
