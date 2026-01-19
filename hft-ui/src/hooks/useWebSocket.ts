import { useEffect, useRef, useCallback, useState } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

interface UseWebSocketOptions {
  onConnect?: () => void;
  onDisconnect?: () => void;
}

export function useWebSocket(options: UseWebSocketOptions = {}) {
  const clientRef = useRef<Client | null>(null);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        setConnected(true);
        options.onConnect?.();
      },
      onDisconnect: () => {
        setConnected(false);
        options.onDisconnect?.();
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame);
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
    };
  }, []);

  const subscribe = useCallback(
    <T>(destination: string, callback: (data: T) => void) => {
      if (!clientRef.current?.connected) {
        return () => {};
      }

      const subscription = clientRef.current.subscribe(destination, (message: IMessage) => {
        const data = JSON.parse(message.body) as T;
        callback(data);
      });

      return () => {
        subscription.unsubscribe();
      };
    },
    [connected]
  );

  return { connected, subscribe };
}
