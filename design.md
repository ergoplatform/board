# PBB Design

There will be 3 layers:
* Elections layer.
* Generalized PBB (Public Bulletin Board) layer.
* Context Broker layer.

Each layer corresponds to a REST api. The Context Broker layer is it's api so we don't need to define it, but we need to define how it will be used specifically. 

The Generalized PBB will be based on the paper “A Generic Design for a Public Bulletin Board” (Hauser, Henni, Dubuis). Specifically, our design architecture is different than the one described in the paper, but our design aims to fulfil the 9 properties described in the paper (section 3):

* Sectioned
* Grouped
* Typed
* Ordered
* Chronological
* Interlinked
* Access-Control
* Certified Publishing

The Generalized PBB (GPBB) will implement its REST api and for that it uses the fiware-orion context broker REST api. GPBB will be implemented using Scala and the library Play (or Akka). Scala is a funcional and OO, statically typed system, and it is compiled to Java bytecode, running on the Java VM. Using a statically typed system means that many programming errors can be checked at compile time. Running in the Java VM means that the exe is highly portable, as in principle it can run on any system that supports Java. Also, using a compiled language instead of an interpreted one like Python in general means execution is normally much faster.

The GPBB REST api consists of two basic operations: Get and Post.
* initial accepted PKs

## POST

Publishes a post in the PBB, and returns the board attributes for that post.
POST /bulletin/api/v1/post

message: m,
user_attributes: {
  section: s,              // a hash-like number, must be randomly generated. Base64 encoded, with 132 bits, which means 22 characters
  group: g,
  pk: public key,
  signature: S              // Sign(m,[s,g])
}

Reponse:
board_attributes: {
  index_general: iG,     // index for all messages
  index_section: iS,     // index for all messages in this election
  timestamp: t,
  hash: Hi,              // Hi = H(pi-1) for example. A hash of the previous post *from this section*
  signature: Spost       // Spost = Sign(m, user_attributes, [i,t, Hi])
}

## GET

Sends a query and returns a set of posts retrieved from the PBB corresponding to the filter/query.
GET /bulletin/api/v1/get

query: Q

Response:

response: R,
result_attributes: {
  index_section,        // works as a "snapshot indicator"
  timestamp: t,
  signature: Sget       // Sget = Sign(Q,R,[t])
}

## Section/Election configuration parameters:

* section: s                   // a hash-like number, must be randomly generated. Base64 encoded, with 132 bits, which means 22 characters
* timestamp_server: local_unix
* init_election: start time
* hash_method: sha512
* Access control method:
    - K:// K is a function that gives the set of authorized keys for posting. Can be dynamic or static (K = const). If dynamic: K = K(Pt, user_attrs, board_attrs). 
    - Initialli accepted PKs
    - admin: pk    // pk for the admin
    
* PBB admin: pk

The PBB admin will post the first message of a section, creating the section and setting the initial parameters for the section/election. Following posts in the section will include section admin posts and  post from other parties, when those parties for that section state use signatures with accepted PKs.


