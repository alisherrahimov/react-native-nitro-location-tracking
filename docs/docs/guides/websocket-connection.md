---
sidebar_position: 2
---

# WebSocket Connection

Manage a WebSocket connection for real-time location sync directly on the
module:

```tsx
import { useEffect, useState } from 'react';
import NitroLocationModule from 'react-native-nitro-location-tracking';
import type {
  ConnectionConfig,
  ConnectionState,
} from 'react-native-nitro-location-tracking';

const connectionConfig: ConnectionConfig = {
  wsUrl: 'wss://api.example.com/ws/driver',
  authToken: 'your-auth-token',
  reconnectIntervalMs: 5000,
  maxReconnectAttempts: 10,
};

function RideScreen() {
  const [connectionState, setConnectionState] =
    useState<ConnectionState>('disconnected');
  const [lastMessage, setLastMessage] = useState<string | null>(null);

  useEffect(() => {
    NitroLocationModule.configureConnection(connectionConfig);
    NitroLocationModule.onConnectionStateChange(setConnectionState);
    NitroLocationModule.onMessage(setLastMessage);
    return () => NitroLocationModule.disconnectWebSocket();
  }, []);

  return (
    <View>
      <Text>Connection: {connectionState}</Text>
      <Text>Last message: {lastMessage}</Text>
      <Button title="Connect" onPress={() => NitroLocationModule.connectWebSocket()} />
      <Button title="Disconnect" onPress={() => NitroLocationModule.disconnectWebSocket()} />
      <Button title="Send Ping" onPress={() => NitroLocationModule.sendMessage('ping')} />
    </View>
  );
}
```

:::note
The WebSocket connection (`connectWebSocket` / `sendMessage`) is for real-time
messaging and remains available independently of [Live Push](./live-push).
:::
