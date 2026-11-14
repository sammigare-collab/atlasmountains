import socket,struct,sys
def pkt(i,t,b): b=b.encode()+b'\0\0'; return struct.pack('<iii',len(b)+8,i,t)+b
s=socket.create_connection(('127.0.0.1',25575)); s.settimeout(3600)
s.sendall(pkt(1,3,'atlas')); s.recv(4096)
for cmd in sys.argv[1:]:
    s.sendall(pkt(2,2,cmd)); d=s.recv(4); n=struct.unpack('<i',d)[0]; body=b''
    while len(body)<n: body+=s.recv(n-len(body))
    print('>',cmd); print(body[8:-2].decode(errors='replace'))
