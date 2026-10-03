import { Navigate } from 'react-router-dom';
import { IonContent, IonPage } from '@ionic/react';
import { Loading } from '../components/States';
import { useSession } from '../session/Session';

/** "/" sends each person to their own surface: residents to the map, moderators to the queue, doers to the inbox. */
export default function Start() {
  const { login, me, meLoading, homePath } = useSession();
  if (!login) {
    return <Navigate to="/signin" replace />;
  }
  if (meLoading || !me) {
    return (
      <IonPage>
        <IonContent className="page">{meLoading ? <Loading /> : <Navigate to="/signin" replace />}</IonContent>
      </IonPage>
    );
  }
  return <Navigate to={homePath} replace />;
}
