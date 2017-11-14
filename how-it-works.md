## How it actually works

1. User should create valid post. Valid post is `{message: "some message to keep", usert_attributes: {"section" : "s", "group": "g", "pk": "public_key", "signature": "signed_post"}}`. This payload should be sent to the POST endpoint (bulletin_post) and verified. 
2. Valid post should contains alll fieilds, they all are mandatory. Cause we should determine where this post should go (section and group). Also pk and signature  should be presented to make sure that they are valid.
3. After that board will generate some board attributes according to input data. Board attribues is json object `{"index": 1, "timestamp": 1000, "hash": "some_hash_value", "signature": {"boardPk": "board_public_key", "signature": "signed_by_board_message"}}` The signature string in board attributes should be formed by board's privateKey, also gere is two fileds called index and timestamp. Actually I don't know why we need index if we already can sort messages by timestamp. Need to be discussed design-wise.
   1. Hash for board_attributes is being generated from all previous messages so we can guarantee that nothing been modified or deleted. In blockchain fashion.
    If everything is ok we send this data altogether (message, user_attributes, board_attributes) to the fiware-orion backed to save it.
4. That's all. Nothing hilarious.

### Getting some message
For sure there is a possibility to get any message from any section,group,index combination.