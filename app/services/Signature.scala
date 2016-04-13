package services

import java.util.concurrent.atomic.AtomicLong
import javax.inject._
import ch.bfh.unicrypt.helper.hash.HashAlgorithm
import ch.bfh.unicrypt.helper.hash.HashMethod
import ch.bfh.unicrypt.helper.converter.classes.bytearray.StringToByteArray
import ch.bfh.unicrypt.helper.converter.classes.biginteger.ByteArrayToBigInteger
import ch.bfh.unicrypt.helper.converter.classes.string.ByteArrayToString
import ch.bfh.unicrypt.helper.array.classes.ByteArray;
import ch.bfh.unicrypt.crypto.schemes.encryption.classes.ElGamalEncryptionScheme
import ch.bfh.unicrypt.math.algebra.multiplicative.classes.GStarModSafePrime
import ch.bfh.unicrypt.math.algebra.general.interfaces.Element

import ch.bfh.unicrypt.crypto.schemes.signature.classes.SchnorrSignatureScheme;
import ch.bfh.unicrypt.helper.math.Alphabet;
import ch.bfh.unicrypt.math.algebra.concatenative.classes.StringElement;
import ch.bfh.unicrypt.math.algebra.concatenative.classes.StringMonoid;
import ch.bfh.unicrypt.math.algebra.general.classes.BooleanElement;
import ch.bfh.unicrypt.math.algebra.general.classes.Pair;
import ch.bfh.unicrypt.math.algebra.general.classes.Tuple;
import ch.bfh.unicrypt.math.algebra.multiplicative.classes.GStarModElement;
import java.security.interfaces.DSAPrivateKey;
import ch.bfh.unicrypt.math.algebra.multiplicative.classes.GStarModPrime;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAPublicKey;
import java.security.spec.DSAPrivateKeySpec;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.KeySpec;
import java.security.KeyFactory;
import java.nio.charset.StandardCharsets
import ch.bfh.unicrypt.math.algebra.dualistic.classes.ZModElement;
import ch.bfh.unicrypt.math.algebra.dualistic.classes.ZModPrime;
import java.math.BigInteger;
import play.api.libs.json._
import models._;

case class DSASignature (signerPK: DSAPublicKey, signaturePK: GStarModElement, signature: Pair) extends PostWriteValidator {
  def toSignatureString(): SignatureString = {
    new models.SignatureString(DSAPublicKeyString(signerPK.getY().toString(),
		                                             signerPK.getParams().getP().toString(),
		                                             signerPK.getParams().getQ().toString(),
		                                             signerPK.getParams().getG().toString()), 
		                       signaturePK.convertToString(), 
		                       SignatureElements(signature.getFirst().convertToBigInteger().toString(), 
		                                         signature.getSecond().convertToBigInteger().toString(),
		                                         signature.getFirst().getSet().getZModOrder().getModulus().toString()))
  }  
  
  def verify(base64message: Base64Message) : Boolean = {
    // group
    val g_q = GStarModPrime.getInstance(signerPK.getParams().getP(), signerPK.getParams().getQ())
    val g = g_q.getElement(signerPK.getParams().getG())
    // Schnorr signature scheme
    val schnorr = SchnorrSignatureScheme.getInstance(StringMonoid.getInstance(Alphabet.BASE64), g)
    // message in Element format
    val messageElement = schnorr.getMessageSpace().getElementFrom(base64message.getBigInteger())
    // verify
    schnorr.verify(signaturePK, messageElement, signature).isTrue()
  }
}

object SchnorrSigningDevice {
  
  def  signString(keypair: KeyPair, base64message: Base64Message) : DSASignature = {
     // private key
     val dsaPrivateKey : DSAPrivateKey = keypair.getPrivate().asInstanceOf[DSAPrivateKey]
     val dsaPublicKey : DSAPublicKey = keypair.getPublic().asInstanceOf[DSAPublicKey]
     
     val pair = new KeyPair(dsaPublicKey, dsaPrivateKey)
     // group
     val g_q = GStarModPrime.getInstance(dsaPrivateKey.getParams().getP(), dsaPrivateKey.getParams().getQ())
     val g = g_q.getElement(dsaPrivateKey.getParams().getG())
     // Schnorr signature scheme
     val schnorr = SchnorrSignatureScheme.getInstance(StringMonoid.getInstance(Alphabet.BASE64), g);
     // message in Element format
     val message = schnorr.getMessageSpace().getElementFrom(base64message.getBigInteger())
     // signature keys
		 val keyPair = schnorr.getKeyPairGenerator().generateKeyPair()
		 val privateKey = keyPair.getFirst()
		 val publicKey = keyPair.getSecond()
		 // signature
		 val signature : Pair = schnorr.sign(privateKey, message)
		 
		 new DSASignature(dsaPublicKey, publicKey.asInstanceOf[GStarModElement], signature)
  }
}

object DSASignature {
  
  def fromSignatureString(sig: SignatureString): DSASignature = {
  		 // signer PK reconstruction
  		 val dsaPublicKeySpec : DSAPublicKeySpec = new DSAPublicKeySpec(new BigInteger(sig.signerPK.y), 
  		                                                                   new BigInteger(sig.signerPK.p), 
  		                                                                   new BigInteger(sig.signerPK.q), 
  		                                                                   new BigInteger(sig.signerPK.g))
       val keyFactory : KeyFactory = KeyFactory.getInstance("DSA")
       val dsaPublicKey  = keyFactory.generatePublic(dsaPublicKeySpec).asInstanceOf[DSAPublicKey]
                                   
       // group
       val g_q = GStarModPrime.getInstance(dsaPublicKey.getParams().getP(), dsaPublicKey.getParams().getQ())
       val g2 = g_q.getElement(dsaPublicKey.getParams().getG())
       
       // signature PK reconstruction: GStarModElement
       val publicKey = g_q.getElementFrom(sig.signaturePK)
       // signature reconstruction: Pair[ZModElement]
  		 // first reconstruct the group
  		 val zmod = ZModPrime.getInstance(new BigInteger(sig.signature.zmod))
  		 // then the elements (they share the same zmod group)
  		 val zFirst = zmod.getElement(new BigInteger(sig.signature.first))
  		 val zSecond = zmod.getElement(new BigInteger(sig.signature.second))
  		 // the signature is the pair of elements 
  		 val signaturePair = Pair.getInstance(zFirst, zSecond)
  		 new DSASignature(dsaPublicKey, publicKey, signaturePair)
  }
}