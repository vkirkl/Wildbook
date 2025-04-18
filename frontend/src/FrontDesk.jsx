import React, { useEffect, useState } from "react";
import UnauthenticatedSwitch from "./UnAuthenticatedSwitch";
import AuthenticatedSwitch from "./AuthenticatedSwitch";
import axios from "axios";
import AuthContext from "./AuthProvider";
import getMergeNotifications from "./models/notifications/getMergeNotifications";
import getCollaborationNotifications from "./models/notifications/getCollaborationNotifications";
import LoadingScreen from "./components/LoadingScreen";
import GoogleTagManager from "./GoogleTagManager";
import "./css/scrollBar.css";
import SessionWarning from "./components/SessionWarning";
import {
  sessionWarningTime,
  sessionCountdownTime,
} from "./constants/sessionWarning";
import useGetSiteSettings from "./models/useGetSiteSettings";
import useDocumentTitle from "./hooks/useDocumentTitle";

export default function FrontDesk() {
  useDocumentTitle();
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [collaborationTitle, setCollaborationTitle] = useState();
  const [collaborationData, setCollaborationData] = useState([]);
  const [mergeData, setMergeData] = useState([]);
  const [count, setCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const { data } = useGetSiteSettings();
  const showclassicsubmit = data?.showClassicSubmit;
  const showClassicEncounterSearch = data?.showClassicEncounters;
  const checkLoginStatus = () => {
    axios
      .head("/api/v3/user")
      .then((response) => {
        setIsLoggedIn(response.status === 200);
        setLoading(false);
      })
      .catch((error) => {
        console.log("Error", error);
        setLoading(false);
        setIsLoggedIn(false);
      });
  };

  const getAllNotifications = async () => {
    const { collaborationTitle, collaborationData } =
      await getCollaborationNotifications();
    const mergeData = await getMergeNotifications();
    const count = collaborationData.length + mergeData.length;
    setCollaborationTitle(collaborationTitle);
    setCollaborationData(collaborationData);
    setMergeData(mergeData);
    setCount(count);
  };

  useEffect(() => {
    checkLoginStatus();
    const intervalId = setInterval(() => {
      checkLoginStatus();
    }, 60000);

    return () => clearInterval(intervalId);
  }, []);

  useEffect(() => {
    if (isLoggedIn) {
      getAllNotifications();
    }
  }, [isLoggedIn]);

  if (loading) return <LoadingScreen />;

  if (isLoggedIn) {
    return (
      <AuthContext.Provider
        value={{
          isLoggedIn,
          setIsLoggedIn,
          count,
          collaborationTitle,
          collaborationData,
          mergeData,
          getAllNotifications,
        }}
      >
        <GoogleTagManager />
        <SessionWarning
          sessionWarningTime={sessionWarningTime}
          sessionCountdownTime={sessionCountdownTime}
        />
        <AuthenticatedSwitch
          showclassicsubmit={showclassicsubmit}
          showClassicEncounterSearch={showClassicEncounterSearch}
        />
      </AuthContext.Provider>
    );
  }

  if (!isLoggedIn) {
    return (
      <AuthContext.Provider
        value={{
          isLoggedIn,
        }}
      >
        <GoogleTagManager />
        <UnauthenticatedSwitch showclassicsubmit={showclassicsubmit} />
      </AuthContext.Provider>
    );
  }

  return <h1>Loading</h1>;
}
